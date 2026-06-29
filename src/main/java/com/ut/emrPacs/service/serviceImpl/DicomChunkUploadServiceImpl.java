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
import com.ut.emrPacs.service.service.DicomUploadProgressListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

@Service
public class DicomChunkUploadServiceImpl implements DicomChunkUploadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomChunkUploadServiceImpl.class);
    private static final long DEFAULT_MAX_DICOM_UPLOAD_BYTES = 4L * 1024L * 1024L * 1024L;
    private static final long DEFAULT_MAX_CHUNK_BYTES = 100L * 1024L * 1024L;
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final int MAX_CHUNKS = 20_000;
    private static final String STATE_UPLOADING = "UPLOADING";
    private static final String STATE_READY = "READY";
    private static final String STATE_PROCESSING = "PROCESSING";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String STATE_FAILED = "FAILED";
    private static final String STAGED_ZIP_ENTRY_PREFIX = "dicom-zip-entry-";
    private static final String STAGED_DICOM_SUFFIX = ".dcm";

    private final ConcurrentMap<String, ChunkUploadSession> sessions = new ConcurrentHashMap<>();

    @Autowired
    private DicomUploadServiceImpl dicomUploadService;

    @Autowired
    private MessageService messageService;

    @Value("${app.security.dicom-upload.max-request-bytes:4294967296}")
    private long maxDicomUploadRequestBytes;

    @Value("${pacs.dicom-upload.max-chunk-bytes:104857600}")
    private long maxChunkBytes;

    @Value("${pacs.dicom-upload.chunk-session-ttl-minutes:30}")
    private long sessionTtlMinutes;

    @Value("${pacs.dicom-upload.max-concurrent-processing-jobs:1}")
    private int maxConcurrentProcessingJobs;

    private volatile ExecutorService processingExecutor;

    @PostConstruct
    void cleanupStaleUploadFilesOnStartup() {
        cleanupStaleSessions();
    }

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
                if (session.receivedChunks.cardinality() == session.totalChunks) {
                    session.state = STATE_READY;
                    session.stage = "Ready for server processing";
                }
                session.lastTouchedAt = Instant.now();
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(progress(session, index)), true));
            }
        } catch (Exception error) {
            LOGGER.warn("DICOM chunk upload failed: {}", error.toString());
            return ResponseMessageUtils.makeResponse(false, messageService.message(clientMessage(error, "Unable to upload chunk."), false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> status(String uploadId, HttpServletRequest httpServletRequest) {
        try {
            ChunkUploadSession session = authorizedSession(uploadId);
            synchronized (session.lock) {
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(progress(session, null)), true));
            }
        } catch (Exception error) {
            LOGGER.warn("DICOM chunk upload status failed: {}", error.toString());
            return ResponseMessageUtils.makeResponse(false, messageService.message(clientMessage(error, "Unable to read upload status."), false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> complete(String uploadId, HttpServletRequest httpServletRequest) {
        ChunkUploadSession session;
        SecurityContext workerSecurityContext;
        try {
            session = authorizedSession(uploadId);
            synchronized (session.lock) {
                if (session.finalResponse != null) {
                    return session.finalResponse;
                }
                if (session.completing) {
                    return processingResponse(session);
                }
                if (session.receivedChunks.cardinality() != session.totalChunks) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Upload is not complete yet.", false));
                }
                if (session.receivedBytes != session.totalSize) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Uploaded bytes do not match the expected file size.", false));
                }
                session.completing = true;
                session.state = STATE_PROCESSING;
                session.processingPercent = 1;
                session.stage = "Queued for DICOM server processing";
                session.message = "Upload accepted. Processing continues on the server.";
                session.lastTouchedAt = Instant.now();
                workerSecurityContext = SecurityContextHolder.createEmptyContext();
                workerSecurityContext.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
            }
        } catch (Exception error) {
            LOGGER.warn("DICOM chunk upload complete failed: {}", error.toString());
            return ResponseMessageUtils.makeResponse(false, messageService.message(clientMessage(error, "Unable to complete chunked upload."), false));
        }

        try {
            processingExecutor().submit(() -> processCompletedUpload(session, workerSecurityContext));
        } catch (RejectedExecutionException error) {
            synchronized (session.lock) {
                session.completing = false;
                session.state = STATE_READY;
                session.processingPercent = 0;
                session.stage = "Ready for server processing";
                session.message = "The processing queue is full. Please retry.";
                session.lastTouchedAt = Instant.now();
            }
            LOGGER.warn("DICOM chunk upload processing queue rejected {}: {}", session.uploadId, error.toString());
            return ResponseMessageUtils.makeResponse(false, messageService.message(
                    "The DICOM processing queue is busy. Please retry in a moment.", false));
        }
        return processingResponse(session);
    }

    private void processCompletedUpload(ChunkUploadSession session, SecurityContext workerSecurityContext) {
        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(workerSecurityContext);
        ResponseMessage<BaseResult> response;
        try {
            synchronized (session.lock) {
                session.stage = "Preparing archive";
                session.message = "Processing on the DICOM server.";
                session.lastTouchedAt = Instant.now();
            }
            DicomUploadProgressListener progressListener = (percent, processedItems, totalItems, stage) -> {
                synchronized (session.lock) {
                    if (!session.completing) {
                        return;
                    }
                    session.processingPercent = Math.max(session.processingPercent, clampPercent(percent));
                    session.processedItems = Math.max(0, processedItems);
                    session.totalItems = Math.max(session.processedItems, totalItems);
                    session.stage = trimToNull(stage) == null ? session.stage : stage.trim();
                    session.lastTouchedAt = Instant.now();
                }
            };
            response = dicomUploadService.uploadDicomZipFile(
                    session.hospitalKey,
                    session.dicomServerKey,
                    session.tempPath,
                    session.totalSize,
                    null,
                    progressListener
            );
        } catch (Exception error) {
            LOGGER.error("DICOM chunk upload processing failed for {}: {}", session.uploadId, error.toString(), error);
            response = ResponseMessageUtils.makeResponse(false, messageService.message(clientMessage(error, "Unable to process chunked upload."), false));
        } finally {
            deleteQuietly(session.tempPath);
            SecurityContextHolder.setContext(previousContext);
        }
        synchronized (session.lock) {
            session.completing = false;
            session.finalResponse = response;
            session.successful = response != null && response.isSuccess();
            session.state = session.successful ? STATE_COMPLETED : STATE_FAILED;
            session.processingPercent = session.successful ? 100 : Math.max(1, session.processingPercent);
            session.stage = session.successful ? "Completed" : "Processing failed";
            session.message = session.successful
                    ? "DICOM upload completed."
                    : "DICOM server processing did not complete successfully.";
            session.lastTouchedAt = Instant.now();
        }
    }

    @Override
    public ResponseMessage<BaseResult> abort(String uploadId, HttpServletRequest httpServletRequest) {
        try {
            ChunkUploadSession session = authorizedSession(uploadId);
            synchronized (session.lock) {
                if (session.completing || STATE_PROCESSING.equals(session.state)) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message(
                            "Upload cannot be canceled after DICOM server processing has started.", false));
                }
                if (STATE_COMPLETED.equals(session.state)) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message(
                            "Completed DICOM uploads cannot be canceled.", false));
                }
                sessions.remove(session.uploadId, session);
            }
            deleteQuietly(session.tempPath);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Upload canceled.", true));
        } catch (Exception error) {
            LOGGER.warn("DICOM chunk upload abort failed: {}", error.toString());
            return ResponseMessageUtils.makeResponse(false, messageService.message(clientMessage(error, "Unable to cancel chunked upload."), false));
        }
    }

    @Scheduled(
            initialDelayString = "${pacs.dicom-upload.chunk-cleanup-initial-delay-ms:60000}",
            fixedDelayString = "${pacs.dicom-upload.chunk-cleanup-delay-ms:300000}"
    )
    public void cleanupStaleSessions() {
        Instant cutoff = Instant.now().minus(sessionTtl());
        sessions.forEach((uploadId, session) -> {
            boolean stale;
            synchronized (session.lock) {
                stale = !session.completing && session.lastTouchedAt.isBefore(cutoff);
            }
            if (stale && sessions.remove(uploadId, session)) {
                deleteQuietly(session.tempPath);
            }
        });
        cleanupOrphanChunkFiles(cutoff);
        cleanupOrphanStagedZipEntries(cutoff);
    }

    private void cleanupOrphanChunkFiles(Instant cutoff) {
        try {
            Path chunksDir = dicomUploadService.resolveUploadTempDir().resolve("chunks").normalize();
            if (!Files.isDirectory(chunksDir)) {
                return;
            }
            Set<Path> activePaths = sessions.values().stream()
                    .map(session -> session.tempPath.toAbsolutePath().normalize())
                    .collect(Collectors.toSet());
            try (var files = Files.list(chunksDir)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".part"))
                        .filter(path -> !activePaths.contains(path.toAbsolutePath().normalize()))
                        .filter(path -> isOlderThan(path, cutoff))
                        .forEach(DicomChunkUploadServiceImpl::deleteQuietly);
            }
        } catch (IOException error) {
            LOGGER.warn("Unable to clean orphaned DICOM chunk files: {}", error.toString());
        }
    }

    private void cleanupOrphanStagedZipEntries(Instant cutoff) {
        try {
            Path tempDir = dicomUploadService.resolveUploadTempDir().normalize();
            if (!Files.isDirectory(tempDir)) {
                return;
            }
            try (var files = Files.list(tempDir)) {
                files.filter(Files::isRegularFile)
                        .filter(DicomChunkUploadServiceImpl::isStagedZipEntryFile)
                        .filter(path -> isOlderThan(path, cutoff))
                        .forEach(DicomChunkUploadServiceImpl::deleteQuietly);
            }
        } catch (IOException error) {
            LOGGER.warn("Unable to clean staged DICOM upload files: {}", error.toString());
        }
    }

    private static boolean isStagedZipEntryFile(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        return fileName.startsWith(STAGED_ZIP_ENTRY_PREFIX) && fileName.endsWith(STAGED_DICOM_SUFFIX);
    }

    private static boolean isOlderThan(Path path, Instant cutoff) {
        try {
            FileTime lastModified = Files.getLastModifiedTime(path);
            return lastModified.toInstant().isBefore(cutoff);
        } catch (IOException error) {
            LOGGER.warn("Unable to inspect DICOM chunk file {}: {}", path, error.toString());
            return false;
        }
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
        response.setState(session.state);
        response.setUploadPercent(percent(session.receivedBytes, session.totalSize));
        response.setProcessingPercent(session.processingPercent);
        response.setProcessedItems(session.processedItems);
        response.setTotalItems(session.totalItems);
        response.setStage(session.stage);
        response.setMessage(session.message);
        response.setSuccessful(session.successful);
        return response;
    }

    private static int percent(long value, long total) {
        if (total <= 0L) {
            return 0;
        }
        return clampPercent((int) Math.round((value * 100.0d) / total));
    }

    private static int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private Duration sessionTtl() {
        return Duration.ofMinutes(sessionTtlMinutes > 0L ? sessionTtlMinutes : 30L);
    }

    private ExecutorService processingExecutor() {
        ExecutorService executor = processingExecutor;
        if (executor != null && !executor.isShutdown()) {
            return executor;
        }
        synchronized (this) {
            executor = processingExecutor;
            if (executor == null || executor.isShutdown()) {
                int workers = Math.max(1, Math.min(8, maxConcurrentProcessingJobs));
                processingExecutor = Executors.newFixedThreadPool(
                        workers,
                        Thread.ofVirtual().name("dicom-upload-job-", 0).factory()
                );
            }
            return processingExecutor;
        }
    }

    @PreDestroy
    void shutdownProcessingExecutor() {
        ExecutorService executor = processingExecutor;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private ResponseMessage<BaseResult> processingResponse(ChunkUploadSession session) {
        synchronized (session.lock) {
            return ResponseMessageUtils.makeResponse(
                    true,
                    messageService.message(session.message, List.of(progress(session, null)), true)
            );
        }
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
        private String state = STATE_UPLOADING;
        private int processingPercent;
        private int processedItems;
        private int totalItems;
        private String stage = "Uploading chunks";
        private String message = "Upload in progress.";
        private Boolean successful;
        private ResponseMessage<BaseResult> finalResponse;

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
