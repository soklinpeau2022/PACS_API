package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.pacs.dicomUpload.DicomChunkUploadInitRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicomUpload.DicomChunkUploadInitResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomUpload.DicomUploadResponse;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        when(dicomUploadService.uploadDicomZipFile(eq("H001"), eq("SERVER1"), any(Path.class), eq((long) expected.length), any()))
                .thenAnswer(invocation -> {
                    Path path = invocation.getArgument(2);
                    assertArrayEquals(expected, Files.readAllBytes(path));
                    return success;
                });

        assertTrue(service.uploadChunk(uploadId, 0, chunk("chunk-0", "hello"), new MockHttpServletRequest()).isSuccess());
        assertTrue(service.uploadChunk(uploadId, 1, chunk("chunk-1", " worl"), new MockHttpServletRequest()).isSuccess());
        assertTrue(service.uploadChunk(uploadId, 2, chunk("chunk-2", "d"), new MockHttpServletRequest()).isSuccess());

        ResponseMessage<BaseResult> response = service.complete(uploadId, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(dicomUploadService).uploadDicomZipFile(eq("H001"), eq("SERVER1"), any(Path.class), eq(11L), any());
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
