package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.pacs.dicomUpload.DicomChunkUploadInitRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicomUpload.DicomChunkUploadInitResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomUpload.DicomChunkUploadProgressResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomUpload.DicomUploadResponse;
import com.ut.emrPacs.service.service.DicomUploadProgressListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DicomChunkUploadServiceImplTest {

    @TempDir
    private Path tempDir;

    @Mock
    private DicomUploadServiceImpl dicomUploadService;

    private DicomChunkUploadServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DicomChunkUploadServiceImpl();
        ReflectionTestUtils.setField(service, "dicomUploadService", dicomUploadService);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());
        ReflectionTestUtils.setField(service, "maxDicomUploadRequestBytes", 1024L);
        ReflectionTestUtils.setField(service, "maxChunkBytes", 5L);
        ReflectionTestUtils.setField(service, "sessionTtlMinutes", 30L);
        when(dicomUploadService.resolveUploadTempDir()).thenReturn(tempDir);
        authenticate(99L);
    }

    @AfterEach
    void tearDown() {
        service.shutdownProcessingExecutor();
        SecurityContextHolder.clearContext();
    }

    @Test
    void completeShouldReassembleChunksAndDelegateToDicomUploadService() throws Exception {
        byte[] expected = "hello world".getBytes(StandardCharsets.UTF_8);
        String uploadId = initUpload(expected.length, 5L, 3).getUploadId();
        ResponseMessage<BaseResult> success = ResponseMessageUtils.makeResponse(
                true,
                new MessageService().message("DICOM upload completed.", List.of(new DicomUploadResponse()), true)
        );
        when(dicomUploadService.uploadDicomZipFile(
                eq("H001"), eq("SERVER1"), any(Path.class), eq((long) expected.length), any(), any(DicomUploadProgressListener.class)))
                .thenAnswer(invocation -> {
                    Path path = invocation.getArgument(2);
                    assertArrayEquals(expected, Files.readAllBytes(path));
                    DicomUploadProgressListener listener = invocation.getArgument(5);
                    listener.onProgress(70, 7, 10, "Forwarding DICOM instances");
                    return success;
                });

        assertTrue(service.uploadChunk(uploadId, 0, chunk("chunk-0", "hello"), new MockHttpServletRequest()).isSuccess());
        assertTrue(service.uploadChunk(uploadId, 1, chunk("chunk-1", " worl"), new MockHttpServletRequest()).isSuccess());
        assertTrue(service.uploadChunk(uploadId, 2, chunk("chunk-2", "d"), new MockHttpServletRequest()).isSuccess());

        ResponseMessage<BaseResult> response = service.complete(uploadId, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        DicomChunkUploadProgressResponse status = awaitTerminalStatus(uploadId);
        assertEquals("COMPLETED", status.getState());
        assertEquals(100, status.getProcessingPercent());
        assertTrue(Boolean.TRUE.equals(status.getSuccessful()));
        assertTrue(service.complete(uploadId, new MockHttpServletRequest()).isSuccess());
        verify(dicomUploadService).uploadDicomZipFile(
                eq("H001"), eq("SERVER1"), any(Path.class), eq(11L), any(), any(DicomUploadProgressListener.class));
    }

    @Test
    void abortShouldBeBlockedWhileDicomServerProcessingIsActive() throws Exception {
        byte[] expected = "hello".getBytes(StandardCharsets.UTF_8);
        String uploadId = initUpload(expected.length, 5L, 1).getUploadId();
        assertTrue(service.uploadChunk(uploadId, 0, chunk("chunk-0", "hello"), new MockHttpServletRequest()).isSuccess());

        ResponseMessage<BaseResult> success = ResponseMessageUtils.makeResponse(
                true,
                new MessageService().message("DICOM upload completed.", List.of(new DicomUploadResponse()), true)
        );
        CountDownLatch processingStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        when(dicomUploadService.uploadDicomZipFile(
                eq("H001"), eq("SERVER1"), any(Path.class), eq(5L), any(), any(DicomUploadProgressListener.class)))
                .thenAnswer(invocation -> {
                    DicomUploadProgressListener listener = invocation.getArgument(5);
                    listener.onProgress(55, 5, 10, "Forwarding DICOM instances");
                    processingStarted.countDown();
                    assertTrue(allowCompletion.await(5, TimeUnit.SECONDS));
                    return success;
                });

        AtomicReference<ResponseMessage<BaseResult>> completion = new AtomicReference<>();
        Thread completeThread = Thread.ofPlatform().start(() -> {
            authenticate(99L);
            try {
                completion.set(service.complete(uploadId, new MockHttpServletRequest()));
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        assertTrue(processingStarted.await(5, TimeUnit.SECONDS));
        DicomChunkUploadProgressResponse status = status(uploadId);
        assertEquals("PROCESSING", status.getState());
        assertEquals(55, status.getProcessingPercent());
        assertEquals("Forwarding DICOM instances", status.getStage());
        assertFalse(service.abort(uploadId, new MockHttpServletRequest()).isSuccess());

        allowCompletion.countDown();
        completeThread.join(5000);
        assertNotNull(completion.get());
        assertTrue(completion.get().isSuccess());
        assertEquals("COMPLETED", awaitTerminalStatus(uploadId).getState());
    }

    @Test
    void cleanupShouldNotRemoveStaleSessionWhileDicomServerProcessingIsActive() throws Exception {
        byte[] expected = "hello".getBytes(StandardCharsets.UTF_8);
        String uploadId = initUpload(expected.length, 5L, 1).getUploadId();
        assertTrue(service.uploadChunk(uploadId, 0, chunk("chunk-0", "hello"), new MockHttpServletRequest()).isSuccess());

        ResponseMessage<BaseResult> success = ResponseMessageUtils.makeResponse(
                true,
                new MessageService().message("DICOM upload completed.", List.of(new DicomUploadResponse()), true)
        );
        CountDownLatch processingStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        when(dicomUploadService.uploadDicomZipFile(
                eq("H001"), eq("SERVER1"), any(Path.class), eq(5L), any(), any(DicomUploadProgressListener.class)))
                .thenAnswer(invocation -> {
                    DicomUploadProgressListener listener = invocation.getArgument(5);
                    listener.onProgress(45, 4, 10, "Forwarding DICOM instances");
                    processingStarted.countDown();
                    assertTrue(allowCompletion.await(5, TimeUnit.SECONDS));
                    return success;
                });

        AtomicReference<ResponseMessage<BaseResult>> completion = new AtomicReference<>();
        Thread completeThread = Thread.ofPlatform().start(() -> {
            authenticate(99L);
            try {
                completion.set(service.complete(uploadId, new MockHttpServletRequest()));
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        assertTrue(processingStarted.await(5, TimeUnit.SECONDS));
        ConcurrentMap<String, ?> sessions = (ConcurrentMap<String, ?>) ReflectionTestUtils.getField(service, "sessions");
        assertNotNull(sessions);
        Object session = sessions.get(uploadId);
        assertNotNull(session);
        ReflectionTestUtils.setField(session, "lastTouchedAt", Instant.now().minus(Duration.ofMinutes(5)));

        service.cleanupStaleSessions();

        DicomChunkUploadProgressResponse status = status(uploadId);
        assertEquals("PROCESSING", status.getState());
        assertEquals(45, status.getProcessingPercent());

        allowCompletion.countDown();
        completeThread.join(5000);
        assertNotNull(completion.get());
        assertTrue(completion.get().isSuccess());
        assertEquals("COMPLETED", awaitTerminalStatus(uploadId).getState());
    }

    @Test
    void uploadChunkShouldRejectAnotherUserSession() throws Exception {
        String uploadId = initUpload(5L, 5L, 1).getUploadId();
        authenticate(100L);

        ResponseMessage<BaseResult> response = service.uploadChunk(
                uploadId,
                0,
                chunk("chunk-0", "hello"),
                new MockHttpServletRequest()
        );

        assertFalse(response.isSuccess());
    }

    @Test
    void cleanupShouldDeleteRestartOrphanedChunkFileAfterTtl() throws Exception {
        ReflectionTestUtils.setField(service, "sessionTtlMinutes", 1L);
        Path chunksDir = tempDir.resolve("chunks");
        Files.createDirectories(chunksDir);
        Path orphan = chunksDir.resolve("restart-orphan.part");
        Files.writeString(orphan, "orphan");
        Files.setLastModifiedTime(orphan, FileTime.from(Instant.now().minus(Duration.ofMinutes(2))));

        service.cleanupStaleSessions();

        assertFalse(Files.exists(orphan));
    }

    private DicomChunkUploadInitResponse initUpload(long totalSize, long chunkSize, int totalChunks) {
        DicomChunkUploadInitRequest request = new DicomChunkUploadInitRequest();
        request.setHospitalKey("H001");
        request.setDicomServerKey("SERVER1");
        request.setFileName("study.zip");
        request.setTotalSize(totalSize);
        request.setChunkSize(chunkSize);
        request.setTotalChunks(totalChunks);
        ResponseMessage<BaseResult> response = service.init(request, new MockHttpServletRequest());
        assertTrue(response.isSuccess());
        return (DicomChunkUploadInitResponse) response.getBody().getData().get(0);
    }

    private DicomChunkUploadProgressResponse status(String uploadId) {
        ResponseMessage<BaseResult> response = service.status(uploadId, new MockHttpServletRequest());
        assertTrue(response.isSuccess());
        return (DicomChunkUploadProgressResponse) response.getBody().getData().get(0);
    }

    private DicomChunkUploadProgressResponse awaitTerminalStatus(String uploadId) throws InterruptedException {
        DicomChunkUploadProgressResponse current = status(uploadId);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!"COMPLETED".equals(current.getState()) && !"FAILED".equals(current.getState())) {
            if (System.nanoTime() >= deadline) {
                return current;
            }
            Thread.sleep(10L);
            current = status(uploadId);
        }
        return current;
    }

    private static MockMultipartFile chunk(String name, String value) {
        return new MockMultipartFile("chunk", name, "application/octet-stream", value.getBytes(StandardCharsets.UTF_8));
    }

    private static void authenticate(Long userId) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("admin", "n/a", "ROLE_ADMIN");
        auth.setAuthenticated(true);
        auth.setDetails(new CurrentUserPrincipal(userId, "admin", 11L, "HSP001", "pacs-web", "jti-1", 1L));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
