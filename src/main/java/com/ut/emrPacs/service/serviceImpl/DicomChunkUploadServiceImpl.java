package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.pacs.dicomUpload.DicomChunkUploadInitRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicomUpload.DicomChunkUploadInitResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomUpload.DicomChunkUploadProgressResponse;
import com.ut.emrPacs.service.service.DicomChunkUploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class DicomChunkUploadServiceImpl implements DicomChunkUploadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomChunkUploadServiceImpl.class);
    private static final long DEFAULT_MAX_DICOM_UPLOAD_BYTES = 4L * 1024L * 1024L * 1024L;
    private static final long DEFAULT_MAX_CHUNK_BYTES = 32L * 1024L * 1024L;
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final int MAX_CHUNKS = 20_000;

    private final ConcurrentMap<String, ChunkUploadSession> sessions = new ConcurrentHashMap<>();

    @Autowired
    private DicomUploadServiceImpl dicomUploadService;

    @Autowired
    private MessageService messageService;

    @Value("${app.security.dicom-upload.max-request-bytes:4294967296}")
    private long maxDicomUploadRequestBytes;

    @Value("${pacs.dicom-upload.max-chunk-bytes:33554432}")
    private long maxChunkBytes;

    @Value("${pacs.dicom-upload.chunk-session-ttl-minutes:30}")
    private long sessionTtlMinutes;

    @Override
    public ResponseMessage<BaseResult> init(DicomChunkUploadInitRequest request, HttpServletRequest httpServletRequest) {
        try {
            Long userId = currentUserId();
            if (userId == null) {
                return unauthorized();
            }
            validateInitRequest(request);

            String uploadId = UUID.randomUUID().toString();
            Path chunksDir = dicomUploadService.resolveUploadTempDir().resolve("chunks").normalize();
            Files.createDirectories(chunksDir);
            Path tempPath = chunksDir.resolve(uploadId + ".part").normalize();
            if (!tempPath.startsWith(chunksDir)) {
                throw new IllegalArgumentException("Invalid chunk upload path.");
            }

            ChunkUploadSession session = new ChunkUploadSession(
                    uploadId,
                    userId,
                    trimToNull(request.getHospitalKey()),
                    trimToNull(request.getDicomServerKey()),
                    safeFileName(request.getFileName()),
                    request.getTotalSize(),
                    request.getTotalChunks(),
                    request.getChunkSize(),
                    tempPath
            );
            sessions.put(uploadId, session);

            DicomChunkUploadInitResponse response = new DicomChunkUploadInitResponse();
            response.setUploadId(uploadId);
            response.setFileName(session.fileName);
            response.setTotalSize(session.totalSize);
            response.setTotalChunks(session.totalChunks);
            response.setChunkSize(session.chunkSize);
            response.setExpiresAt(DateTimeFormatter.ISO_INSTANT.format(session.lastTouchedAt.plus(sessionTtl())));
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(response), true));
        } catch (Exception error) {
            LOGGER.warn("DICOM chunk upload init failed: {}", error.toString());
            return ResponseMessageUtils.makeResponse(false, messageService.message(clientMessage(error, "Unable to start chunked upload."), false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> uploadChunk(String uploadId, Integer index, MultipartFile chunk, HttpServletRequest httpServletRequest) {
        try {
            ChunkUploadSession session = authorizedSession(uploadId);
            validateChunk(index, chunk, session);
            long expectedBytes = expectedChunkBytes(index, session);
            long offset = (long) index * session.chunkSize;
            synchronized (session.lock) {
                if (session.completing) {
                    throw new IllegalStateException("Upload is already processing.");
                }
                if (session.receivedChunks.get(index)) {
                    session.lastTouchedAt = Instant.now();
                    return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(progress(session, index)), true));
                }
                long written = writeChunk(session.tempPath, offset, chunk, expectedBytes);
                if (written != expectedBytes) {
                    throw new IllegalArgumentException("Chunk size does not match the upload session.");
                }
                session.receivedBytes += written;
                session.receivedChunks.set(index);
                session.lastTouchedAt = Instant.now();
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(progress(session, index)), true));
            }
        } catch (Exception error) {
            LOGGER.warn("DICOM chunk upload failed: {}", error.toString());
            return ResponseMessageUtils.makeResponse(false, messageService.message(clientMessage(error, "Unable to upload chunk."), false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> complete(String uploadId, HttpServletRequest httpServletRequest) {
        ChunkUploadSession session;
        try {
            session = authorizedSession(uploadId);
            synchronized (session.lock) {
                if (session.receivedChunks.cardinality() != session.totalChunks) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Upload is not complete yet.", false));
                }
                if (session.receivedBytes != session.totalSize) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Uploaded bytes do not match the expected file size.", false));
                }
                session.completing = true;
                session.lastTouchedAt = Instant.now();
            }
        } catch (Exception error) {
            LOGGER.warn("DICOM chunk upload complete failed: {}", error.toString());
            return ResponseMessageUtils.makeResponse(false, messageService.message(clientMessage(error, "Unable to complete chunked upload."), false));
        }

        try {
            return dicomUploadService.uploadDicomZipFile(
                    session.hospitalKey,
                    session.dicomServerKey,
                    session.tempPath,
                    session.totalSize,
                    httpServletRequest
            );
        } finally {
            sessions.remove(session.uploadId);
            deleteQuietly(session.tempPath);
        }
    }

    @Override
    public ResponseMessage<BaseResult> abort(String uploadId, HttpServletRequest httpServletRequest) {
        try {
            ChunkUploadSession session = authorizedSession(uploadId);
            sessions.remove(session.uploadId);
            deleteQuietly(session.tempPath);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Upload canceled.", true));
        } catch (Exception error) {
            LOGGER.warn("DICOM chunk upload abort failed: {}", error.toString());
            return ResponseMessageUtils.makeResponse(false, messageService.message(clientMessage(error, "Unable to cancel chunked upload."), false));
        }
    }

    @Scheduled(fixedDelayString = "${pacs.dicom-upload.chunk-cleanup-delay-ms:300000}")
    public void cleanupStaleSessions() {
        Instant cutoff = Instant.now().minus(sessionTtl());
        sessions.forEach((uploadId, session) -> {
            if (session.lastTouchedAt.isBefore(cutoff) && sessions.remove(uploadId, session)) {
                deleteQuietly(session.tempPath);
            }
        });
    }

    private void validateInitRequest(DicomChunkUploadInitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Invalid chunk upload request.");
        }
        long totalSize = request.getTotalSize() == null ? 0L : request.getTotalSize();
        long chunkSize = request.getChunkSize() == null ? 0L : request.getChunkSize();
        int totalChunks = request.getTotalChunks() == null ? 0 : request.getTotalChunks();
        if (totalSize <= 0L) {
            throw new IllegalArgumentException("Upload file is empty.");
        }
        if (totalSize > configuredMaxUploadBytes()) {
            throw new IllegalArgumentException("DICOM upload can be up to " + formatBytes(configuredMaxUploadBytes()) + " per request.");
        }
        if (chunkSize <= 0L || chunkSize > configuredMaxChunkBytes()) {
            throw new IllegalArgumentException("Upload chunk is too large.");
        }
        if (totalChunks <= 0 || totalChunks > MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid upload chunk count.");
        }
        long expectedChunks = (totalSize + chunkSize - 1L) / chunkSize;
        if (expectedChunks != totalChunks) {
            throw new IllegalArgumentException("Upload chunk metadata is invalid.");
        }
    }

    private void validateChunk(Integer index, MultipartFile chunk, ChunkUploadSession session) {
        if (index == null || index < 0 || index >= session.totalChunks) {
            throw new IllegalArgumentException("Invalid upload chunk index.");
        }
        if (chunk == null || chunk.isEmpty()) {
            throw new IllegalArgumentException("Upload chunk is empty.");
        }
        long expectedBytes = expectedChunkBytes(index, session);
        if (chunk.getSize() != expectedBytes) {
            throw new IllegalArgumentException("Chunk size does not match the upload session.");
        }
    }

    private ChunkUploadSession authorizedSession(String uploadId) {
        String safeUploadId = normalizeUploadId(uploadId);
        ChunkUploadSession session = sessions.get(safeUploadId);
        if (session == null) {
            throw new IllegalArgumentException("Upload session was not found or expired.");
        }
        Long userId = currentUserId();
        if (userId == null || !userId.equals(session.userId)) {
            throw new IllegalArgumentException("Upload session does not belong to this user.");
        }
        return session;
    }

    private static String normalizeUploadId(String uploadId) {
        String normalized = trimToNull(uploadId);
        if (normalized == null) {
            throw new IllegalArgumentException("Upload id is required.");
        }
        try {
            return UUID.fromString(normalized).toString();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Upload id is invalid.");
        }
    }

    private static long writeChunk(Path path, long offset, MultipartFile chunk, long expectedBytes) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        long total = 0L;
        try (InputStream inputStream = chunk.getInputStream();
             RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.seek(offset);
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > expectedBytes) {
                    throw new IllegalArgumentException("Chunk size does not match the upload session.");
                }
                file.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static long expectedChunkBytes(int index, ChunkUploadSession session) {
        long offset = (long) index * session.chunkSize;
        return Math.min(session.chunkSize, session.totalSize - offset);
    }

    private static DicomChunkUploadProgressResponse progress(ChunkUploadSession session, Integer index) {
        DicomChunkUploadProgressResponse response = new DicomChunkUploadProgressResponse();
        response.setUploadId(session.uploadId);
        response.setIndex(index);
        response.setReceivedChunks(session.receivedChunks.cardinality());
        response.setTotalChunks(session.totalChunks);
        response.setReceivedBytes(session.receivedBytes);
        response.setTotalSize(session.totalSize);
        response.setComplete(session.receivedChunks.cardinality() == session.totalChunks);
        return response;
    }

    private Duration sessionTtl() {
        return Duration.ofMinutes(sessionTtlMinutes > 0L ? sessionTtlMinutes : 30L);
    }

    private long configuredMaxUploadBytes() {
        return maxDicomUploadRequestBytes > 0L ? maxDicomUploadRequestBytes : DEFAULT_MAX_DICOM_UPLOAD_BYTES;
    }

    private long configuredMaxChunkBytes() {
        return maxChunkBytes > 0L ? maxChunkBytes : DEFAULT_MAX_CHUNK_BYTES;
    }

    private static Long currentUserId() {
        CurrentUserPrincipal principal = UserAuthSession.getCurrentUser();
        return principal == null ? null : principal.userId();
    }

    private ResponseMessage<BaseResult> unauthorized() {
        return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            LOGGER.warn("Unable to delete chunk upload temp file {}: {}", path, error.toString());
        }
    }

    private static String safeFileName(String fileName) {
        String trimmed = trimToNull(fileName);
        if (trimmed == null) {
            return "upload.zip";
        }
        String normalized = trimmed.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String clientMessage(Exception error, String fallback) {
        String message = trimToNull(error.getMessage());
        return message == null ? fallback : message;
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f GB", bytes / (1024d * 1024d * 1024d));
        }
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024d * 1024d));
        }
        if (bytes >= 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024d);
        }
        return bytes + " bytes";
    }

    private static final class ChunkUploadSession {
        private final Object lock = new Object();
        private final String uploadId;
        private final Long userId;
        private final String hospitalKey;
        private final String dicomServerKey;
        private final String fileName;
        private final long totalSize;
        private final int totalChunks;
        private final long chunkSize;
        private final Path tempPath;
        private final BitSet receivedChunks = new BitSet();
        private volatile Instant lastTouchedAt = Instant.now();
        private long receivedBytes;
        private boolean completing;

        private ChunkUploadSession(
                String uploadId,
                Long userId,
                String hospitalKey,
                String dicomServerKey,
                String fileName,
                long totalSize,
                int totalChunks,
                long chunkSize,
                Path tempPath
        ) {
            this.uploadId = uploadId;
            this.userId = userId;
            this.hospitalKey = hospitalKey;
            this.dicomServerKey = dicomServerKey;
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.totalChunks = totalChunks;
            this.chunkSize = chunkSize;
            this.tempPath = tempPath;
        }
    }
}
