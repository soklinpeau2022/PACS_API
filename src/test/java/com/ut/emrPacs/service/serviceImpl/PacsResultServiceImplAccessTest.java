package com.ut.emrPacs.service.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ut.emrPacs.authentication.util.ViewerAccessKeyService;
import com.ut.emrPacs.authentication.util.ViewerAccessKeyService.ViewerAccessClaims;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.mapper.hospital.HospitalMapper;
import com.ut.emrPacs.mapper.modality.ModalityMapper;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.mapper.pacs.PacsResultMapper;
import com.ut.emrPacs.mapper.pacs.StudyMapper;
import com.ut.emrPacs.mapper.pacs.WorklistMapper;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultContextRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultFindByWorklistRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultImageUploadRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultSaveRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateChunkCompleteRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateChunkRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateRequest;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultContextResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsViewerStateResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import com.ut.emrPacs.model.dto.response.systemSettings.hospital.HospitalResponseDetail;
import com.ut.emrPacs.model.dto.response.systemSettings.modality.ModalityResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacsResultServiceImplAccessTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private PacsResultMapper pacsResultMapper;
    @Mock
    private WorklistMapper worklistMapper;
    @Mock
    private StudyMapper studyMapper;
    @Mock
    private DicomServerMapper dicomServerMapper;
    @Mock
    private HospitalMapper hospitalMapper;
    @Mock
    private ModalityMapper modalityMapper;
    @Mock
    private ViewerAccessKeyService viewerAccessKeyService;
    @Mock
    private PublicEntityKeyResolver publicEntityKeyResolver;
    @Mock
    private HttpServletRequest request;

    @TempDir
    private Path uploadRoot;

    private PacsResultServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PacsResultServiceImpl();
        ReflectionTestUtils.setField(service, "pacsResultMapper", pacsResultMapper);
        ReflectionTestUtils.setField(service, "WorklistMapper", worklistMapper);
        ReflectionTestUtils.setField(service, "studyMapper", studyMapper);
        ReflectionTestUtils.setField(service, "dicomServerMapper", dicomServerMapper);
        ReflectionTestUtils.setField(service, "hospitalMapper", hospitalMapper);
        ReflectionTestUtils.setField(service, "modalityMapper", modalityMapper);
        ReflectionTestUtils.setField(service, "viewerAccessKeyService", viewerAccessKeyService);
        ReflectionTestUtils.setField(service, "publicEntityKeyResolver", publicEntityKeyResolver);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());
        ReflectionTestUtils.setField(service, "staticAuthEnabled", true);
        ReflectionTestUtils.setField(service, "configuredApiKey", "server-side-key");
        ReflectionTestUtils.setField(service, "uploadRoot", uploadRoot.toString());
        ReflectionTestUtils.setField(service, "maxImageBytes", 10_485_760L);
        lenient().when(publicEntityKeyResolver.resolve(
                        any(PublicEntityKeyResolver.Entity.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        lenient().when(request.getHeader(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "X-PACS-VIEWER-ACCESS" -> "viewer-token";
            case "X-PACS-RESULT-API-KEY" -> "server-side-key";
            default -> null;
        });
    }

    @Test
    void readOnlyViewerTokenCanResolveContext() throws Exception {
        ViewerAccessClaims claims = claims(ViewerAccessKeyService.ACCESS_READ, 99L);
        when(viewerAccessKeyService.decode("viewer-token")).thenReturn(claims);

        PacsResultContextRequest contextRequest = new PacsResultContextRequest();
        contextRequest.setHospitalId(1L);
        contextRequest.setWorklistId(7L);
        contextRequest.setStudyId(22L);
        contextRequest.setModalityId(3L);
        contextRequest.setStudyInstanceUid("1.2.3");

        PacsResultContextResponse context = new PacsResultContextResponse();
        context.setHospitalId(1L);
        context.setWorklistId(7L);
        context.setStudyId(22L);
        context.setModalityId(3L);
        context.setStudyInstanceUid("1.2.3");
        context.setStatus("IMAGE_RECEIVED");
        when(pacsResultMapper.findContextByWorklistId(contextRequest)).thenReturn(context);

        var response = service.getContext(contextRequest, request);

        assertTrue(response.isSuccess());
    }

    @Test
    void publicViewerTokenCannotCreateResult() throws Exception {
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_PUBLIC, null));

        PacsResultSaveRequest saveRequest = new PacsResultSaveRequest();
        saveRequest.setHospitalId(1L);
        saveRequest.setWorklistId(7L);
        saveRequest.setStudyId(22L);
        saveRequest.setModalityId(3L);
        saveRequest.setStudyInstanceUid("1.2.3");
        saveRequest.setResultText("Normal");

        var response = service.create(saveRequest, List.of(), request);

        assertFalse(response.isSuccess());
    }

    @Test
    void browserResultCreateUsesViewerTokenWithoutServerApiKey() throws Exception {
        when(request.getHeader(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "X-PACS-VIEWER-ACCESS" -> "viewer-token";
            default -> null;
        });
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));
        when(worklistMapper.findWorklistById(1L, 7L)).thenReturn(worklistContext());
        when(modalityMapper.countActiveModalitiesByIds(List.of(3L))).thenReturn(1L);
        when(modalityMapper.countActiveHospitalModality(1L, 3L)).thenReturn(1L);
        when(pacsResultMapper.findExisting(any(PacsResultSaveRequest.class))).thenReturn(null);
        when(pacsResultMapper.insertResult(any(PacsResultSaveRequest.class), eq("COMPLETED"), eq(99L)))
                .thenReturn(88L);
        PacsResultResponse saved = resultResponse(88L, 99L);
        saved.setResultText("<p>GGGG</p>");
        when(pacsResultMapper.findById(88L)).thenReturn(saved);
        when(pacsResultMapper.listImages(88L)).thenReturn(List.of());

        PacsResultSaveRequest saveRequest = saveRequest();
        saveRequest.setResultText("<p>GGGG</p>");
        saveRequest.setCompleted(true);

        var response = service.createBrowser(saveRequest, List.of(), request);

        assertTrue(response.isSuccess());
        verify(pacsResultMapper).insertResult(any(PacsResultSaveRequest.class), eq("COMPLETED"), eq(99L));
    }

    @Test
    void legacyResultCreateStillRequiresServerApiKey() throws Exception {
        when(request.getHeader(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "X-PACS-VIEWER-ACCESS" -> "viewer-token";
            default -> null;
        });
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        PacsResultSaveRequest saveRequest = saveRequest();
        saveRequest.setResultText("<p>GGGG</p>");
        saveRequest.setCompleted(true);

        var response = service.create(saveRequest, List.of(), request);

        assertFalse(response.isSuccess());
        verify(pacsResultMapper, never()).insertResult(any(PacsResultSaveRequest.class), anyString(), any());
    }

    @Test
    void publicViewerTokenCannotSaveViewerState() throws Exception {
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_PUBLIC, null));

        var response = service.saveBrowserViewerState(new PacsViewerStateRequest(), request);

        assertFalse(response.isSuccess());
        verify(pacsResultMapper, never()).insertViewerState(any(PacsViewerStateRequest.class), any());
        verify(pacsResultMapper, never()).updateViewerState(any(PacsViewerStateRequest.class), any());
    }

    @Test
    void publicViewerTokenCannotDeleteViewerState() throws Exception {
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_PUBLIC, null));

        var response = service.deleteBrowserViewerState(new PacsViewerStateRequest(), request);

        assertFalse(response.isSuccess());
        verify(pacsResultMapper, never()).deactivateViewerState(any(PacsViewerStateRequest.class), any());
    }

    @Test
    void viewerStateReadRejectsSuppliedPublicKeyThatDoesNotResolve() throws Exception {
        when(publicEntityKeyResolver.resolve(
                PublicEntityKeyResolver.Entity.HOSPITAL, "hospital-key", null
        )).thenReturn(1L);
        when(publicEntityKeyResolver.resolve(
                PublicEntityKeyResolver.Entity.WORKLIST, "invalid-worklist-key", null
        )).thenReturn(null);

        PacsViewerStateRequest stateRequest = new PacsViewerStateRequest();
        stateRequest.setHospitalKey("hospital-key");
        stateRequest.setWorklistKey("invalid-worklist-key");
        stateRequest.setStudyInstanceUid("1.2.3");

        var response = service.findBrowserViewerState(stateRequest, request);

        assertFalse(response.isSuccess());
        assertEquals(403, response.getHeader().getStatusCode());
        verify(pacsResultMapper, never()).findViewerState(any(PacsViewerStateRequest.class));
    }

    @Test
    void editingDoctorCannotUpdateAnotherDoctorsResult() throws Exception {
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        PacsResultResponse existing = new PacsResultResponse();
        existing.setId(12L);
        existing.setHospitalId(1L);
        existing.setWorklistId(7L);
        existing.setStudyId(22L);
        existing.setModalityId(3L);
        existing.setStudyInstanceUid("1.2.3");
        existing.setCreatedBy(77L);
        when(pacsResultMapper.findById(12L)).thenReturn(existing);

        PacsResultSaveRequest saveRequest = new PacsResultSaveRequest();
        saveRequest.setId(12L);
        saveRequest.setHospitalId(1L);
        saveRequest.setWorklistId(7L);
        saveRequest.setStudyId(22L);
        saveRequest.setModalityId(3L);
        saveRequest.setStudyInstanceUid("1.2.3");
        saveRequest.setResultText("Update");

        var response = service.update(saveRequest, List.of(), request);

        assertFalse(response.isSuccess());
    }

    @Test
    void uploadImagesStoresUnderHospitalAndModalityFolders() throws Exception {
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        PacsResultResponse existing = new PacsResultResponse();
        existing.setId(44L);
        existing.setHospitalId(1L);
        existing.setWorklistId(7L);
        existing.setStudyId(22L);
        existing.setModalityId(3L);
        existing.setStudyInstanceUid("1.2.3");
        existing.setCreatedBy(99L);
        when(pacsResultMapper.findById(44L)).thenReturn(existing);
        when(pacsResultMapper.listImages(44L)).thenReturn(List.of());
        when(pacsResultMapper.nextImageSortOrder(44L)).thenReturn(0);

        HospitalResponseDetail hospital = new HospitalResponseDetail();
        hospital.setAbbr("KSFH");
        hospital.setCode("KSFH");
        hospital.setHospitalName("KSFH Hospital");
        when(hospitalMapper.getHospitalById(1L)).thenReturn(List.of(hospital));

        ModalityResponse modality = new ModalityResponse();
        modality.setAbbr("CT");
        modality.setName("Computed Tomography");
        when(modalityMapper.getModalityById(3L)).thenReturn(List.of(modality));

        PacsResultImageUploadRequest uploadRequest = new PacsResultImageUploadRequest();
        uploadRequest.setResultId(44L);
        MockMultipartFile image = new MockMultipartFile(
                "images",
                "result.png",
                "image/png",
                pngBytes()
        );

        var response = service.uploadImages(uploadRequest, List.of(image), request);

        assertTrue(response.isSuccess());
        Path expectedFolder = uploadRoot.resolve("KSFH").resolve("CT");
        assertTrue(Files.isDirectory(expectedFolder));

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(pacsResultMapper).insertImage(
                eq(44L),
                eq(1L),
                eq(3L),
                pathCaptor.capture(),
                eq("result.png"),
                eq("image/png"),
                anyLong(),
                eq(0)
        );
        assertTrue(pathCaptor.getValue().replace('\\', '/').contains("/KSFH/CT/"));
        assertEquals(1L, Files.list(expectedFolder).count());
    }

    @Test
    void saveViewerStateStoresTypedSegmentationPayloads() throws Exception {
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        PacsViewerStateRequest saveRequest = new PacsViewerStateRequest();
        saveRequest.setViewerState(JSON.readTree("{\"source\":\"ohif-viewer\"}"));
        saveRequest.setLabelmapSegmentations(JSON.readTree("[{\"segmentationId\":\"seg-label\",\"representationTypes\":[\"Labelmap\"],\"labelmap\":{\"sparseLabelmap\":{\"totalVoxels\":2}}}]"));
        saveRequest.setContourSegmentations(JSON.readTree("[{\"segmentationId\":\"seg-contour\",\"representationTypes\":[\"Contour\"],\"contour\":{\"annotationUIDsBySegment\":{\"1\":[\"anno-1\"]}}}]"));
        saveRequest.setSurfaceSegmentations(JSON.readTree("[{\"segmentationId\":\"seg-surface\",\"representationTypes\":[\"Surface\"],\"surface\":{\"data\":{\"geometryIds\":[\"geo-1\"]}}}]"));
        saveRequest.setPresentationState(JSON.readTree("{\"segmentationPresentation\":[{\"segmentationId\":\"seg-label\",\"type\":\"Labelmap\"}]}"));
        saveRequest.setToolState(JSON.readTree("{\"activeTool\":\"SplineROI\"}"));

        PacsViewerStateResponse savedResponse = new PacsViewerStateResponse();
        savedResponse.setId(55L);
        savedResponse.setHospitalId(1L);
        savedResponse.setWorklistId(7L);
        savedResponse.setStudyId(22L);
        savedResponse.setModalityId(3L);
        savedResponse.setStudyInstanceUid("1.2.3");
        savedResponse.setStateType("OHIF_VIEWER_STATE");
        savedResponse.setSchemaVersion(2);
        savedResponse.setViewerStateJson("{}");
        savedResponse.setMeasurementsJson("[]");
        savedResponse.setAnnotationsJson("[]");
        savedResponse.setSegmentationsJson("[]");
        savedResponse.setLabelmapSegmentationsJson("[]");
        savedResponse.setContourSegmentationsJson("[]");
        savedResponse.setSurfaceSegmentationsJson("[]");
        savedResponse.setAdditionalFindingsJson("[]");
        savedResponse.setPresentationStateJson("{}");
        savedResponse.setToolStateJson("{}");
        savedResponse.setMetadataJson("{}");

        when(pacsResultMapper.findViewerState(any(PacsViewerStateRequest.class)))
                .thenReturn(null, savedResponse);
        when(pacsResultMapper.insertViewerState(any(PacsViewerStateRequest.class), eq(99L)))
                .thenReturn(55L);

        var response = service.saveViewerState(saveRequest, request);

        assertTrue(response.isSuccess());
        ArgumentCaptor<PacsViewerStateRequest> requestCaptor =
                ArgumentCaptor.forClass(PacsViewerStateRequest.class);
        verify(pacsResultMapper).insertViewerState(requestCaptor.capture(), eq(99L));
        PacsViewerStateRequest persisted = requestCaptor.getValue();
        assertEquals(2, persisted.getSchemaVersion());
        assertTrue(persisted.getSegmentationsJson().contains("seg-label"));
        assertTrue(persisted.getSegmentationsJson().contains("seg-contour"));
        assertTrue(persisted.getSegmentationsJson().contains("seg-surface"));
        assertTrue(persisted.getLabelmapSegmentationsJson().contains("seg-label"));
        assertTrue(persisted.getContourSegmentationsJson().contains("seg-contour"));
        assertTrue(persisted.getSurfaceSegmentationsJson().contains("seg-surface"));
        assertTrue(persisted.getPresentationStateJson().contains("segmentationPresentation"));
        assertTrue(persisted.getToolStateJson().contains("SplineROI"));
    }

    @Test
    void browserViewerStateFindUsesOnlyScopedViewerToken() throws Exception {
        when(request.getHeader(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "X-PACS-VIEWER-ACCESS" -> "viewer-token";
            default -> null;
        });
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_READ, 99L));
        when(pacsResultMapper.findViewerState(any(PacsViewerStateRequest.class))).thenReturn(null);

        var response = service.findBrowserViewerState(new PacsViewerStateRequest(), request);

        assertTrue(response.isSuccess());
    }

    @Test
    void legacyViewerStateFindStillRequiresServerApiKey() throws Exception {
        when(request.getHeader(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "X-PACS-VIEWER-ACCESS" -> "viewer-token";
            default -> null;
        });
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_READ, 99L));

        var response = service.findViewerState(new PacsViewerStateRequest(), request);

        assertFalse(response.isSuccess());
    }

    @Test
    void browserViewerStateSaveRejectsIncorrectJsonTypes() throws Exception {
        when(request.getHeader(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "X-PACS-VIEWER-ACCESS" -> "viewer-token";
            default -> null;
        });
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        PacsViewerStateRequest saveRequest = new PacsViewerStateRequest();
        saveRequest.setMeasurements(JSON.readTree("{\"not\":\"an-array\"}"));

        var response = service.saveBrowserViewerState(saveRequest, request);

        assertFalse(response.isSuccess());
        verify(pacsResultMapper, never()).insertViewerState(any(PacsViewerStateRequest.class), any());
    }

    @Test
    void browserViewerStateSaveAcceptsAndAccountsForEightHundredKilobytes() throws Exception {
        when(request.getHeader(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "X-PACS-VIEWER-ACCESS" -> "viewer-token";
            default -> null;
        });
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        PacsViewerStateRequest saveRequest = new PacsViewerStateRequest();
        saveRequest.setMetadata(JSON.createObjectNode().put("performancePayload", "x".repeat(800 * 1024)));

        PacsViewerStateResponse savedResponse = emptyViewerStateResponse(71L);
        when(pacsResultMapper.findViewerState(any(PacsViewerStateRequest.class)))
                .thenReturn(null, savedResponse);
        when(pacsResultMapper.insertViewerState(any(PacsViewerStateRequest.class), eq(99L)))
                .thenReturn(71L);

        var response = service.saveBrowserViewerState(saveRequest, request);

        assertTrue(response.isSuccess());
        ArgumentCaptor<PacsViewerStateRequest> requestCaptor =
                ArgumentCaptor.forClass(PacsViewerStateRequest.class);
        verify(pacsResultMapper).insertViewerState(requestCaptor.capture(), eq(99L));
        PacsViewerStateRequest persisted = requestCaptor.getValue();
        assertTrue(persisted.getPayloadSizeBytes() >= 800L * 1024L);
        assertTrue(persisted.getPayloadSizeBytes() < 900L * 1024L);
        assertNotNull(persisted.getPayloadSha256());
        assertEquals(64, persisted.getPayloadSha256().length());
    }

    @Test
    void browserViewerStateChunkedSaveReassemblesAndPersists() throws Exception {
        when(request.getHeader(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "X-PACS-VIEWER-ACCESS" -> "viewer-token";
            default -> null;
        });
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        String payload = """
                {"viewerState":{"source":"ohif-viewer"},"metadata":{"chunked":true},"contourSegmentations":[{"segmentationId":"seg-1","contour":{"annotationUIDsBySegment":{"1":["anno-1"]}}}]}""";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String uploadId = "chunk-test-1";
        String payloadSha256 = sha256(payloadBytes);
        int splitAt = 80;
        PacsViewerStateResponse savedResponse = emptyViewerStateResponse(91L);
        when(pacsResultMapper.findViewerState(any(PacsViewerStateRequest.class)))
                .thenReturn(null, savedResponse);
        when(pacsResultMapper.insertViewerState(any(PacsViewerStateRequest.class), eq(99L)))
                .thenReturn(91L);

        assertTrue(service.saveBrowserViewerStateChunk(
                chunk(uploadId, 0, 2, payloadBytes, payloadSha256, 0, splitAt),
                request
        ).isSuccess());
        assertTrue(service.saveBrowserViewerStateChunk(
                chunk(uploadId, 1, 2, payloadBytes, payloadSha256, splitAt, payloadBytes.length),
                request
        ).isSuccess());

        PacsViewerStateChunkCompleteRequest complete = complete(uploadId, 2, payloadBytes, payloadSha256);
        var response = service.completeBrowserViewerStateChunk(complete, request);

        assertTrue(response.isSuccess());
        ArgumentCaptor<PacsViewerStateRequest> requestCaptor =
                ArgumentCaptor.forClass(PacsViewerStateRequest.class);
        verify(pacsResultMapper).insertViewerState(requestCaptor.capture(), eq(99L));
        PacsViewerStateRequest persisted = requestCaptor.getValue();
        assertTrue(persisted.getViewerStateJson().contains("ohif-viewer"));
        assertTrue(persisted.getMetadataJson().contains("chunked"));
        assertTrue(persisted.getContourSegmentationsJson().contains("seg-1"));
    }

    @Test
    void browserViewerStateChunkedSaveRejectsIncompleteUpload() throws Exception {
        when(request.getHeader(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "X-PACS-VIEWER-ACCESS" -> "viewer-token";
            default -> null;
        });
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        byte[] payloadBytes = "{\"viewerState\":{\"source\":\"ohif-viewer\"}}".getBytes(StandardCharsets.UTF_8);
        String uploadId = "chunk-test-incomplete";
        String payloadSha256 = sha256(payloadBytes);

        assertTrue(service.saveBrowserViewerStateChunk(
                chunk(uploadId, 0, 2, payloadBytes, payloadSha256, 0, 10),
                request
        ).isSuccess());

        var response = service.completeBrowserViewerStateChunk(
                complete(uploadId, 2, payloadBytes, payloadSha256),
                request
        );

        assertFalse(response.isSuccess());
        verify(pacsResultMapper, never()).insertViewerState(any(PacsViewerStateRequest.class), any());
        verify(pacsResultMapper, never()).updateViewerState(any(PacsViewerStateRequest.class), any());
    }

    @Test
    void readOnlyViewerCannotUploadViewerStateChunk() throws Exception {
        when(request.getHeader(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "X-PACS-VIEWER-ACCESS" -> "viewer-token";
            default -> null;
        });
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_READ, 99L));

        byte[] payloadBytes = "{\"viewerState\":{\"source\":\"ohif-viewer\"}}".getBytes(StandardCharsets.UTF_8);
        String payloadSha256 = sha256(payloadBytes);

        var response = service.saveBrowserViewerStateChunk(
                chunk("chunk-test-read-only", 0, 1, payloadBytes, payloadSha256, 0, payloadBytes.length),
                request
        );

        assertFalse(response.isSuccess());
        verify(pacsResultMapper, never()).insertViewerState(any(PacsViewerStateRequest.class), any());
        verify(pacsResultMapper, never()).updateViewerState(any(PacsViewerStateRequest.class), any());
    }

    @Test
    void browserViewerStateSaveRejectsUnknownViewerStateKeyBeforeInsert() throws Exception {
        when(request.getHeader(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "X-PACS-VIEWER-ACCESS" -> "viewer-token";
            default -> null;
        });
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));
        when(pacsResultMapper.findViewerState(any(PacsViewerStateRequest.class))).thenReturn(null);

        PacsViewerStateRequest saveRequest = new PacsViewerStateRequest();
        saveRequest.setViewerStateKey("4d2383d4-52db-44ba-afd9-115d0a8eb383");

        var response = service.saveBrowserViewerState(saveRequest, request);

        assertFalse(response.isSuccess());
        verify(pacsResultMapper, never()).insertViewerState(any(PacsViewerStateRequest.class), any());
        verify(pacsResultMapper, never()).updateViewerState(any(PacsViewerStateRequest.class), any());
    }

    @Test
    void readOnlyViewerCannotDeleteViewerState() throws Exception {
        when(request.getHeader(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "X-PACS-VIEWER-ACCESS" -> "viewer-token";
            default -> null;
        });
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_READ, 99L));

        var response = service.deleteBrowserViewerState(new PacsViewerStateRequest(), request);

        assertFalse(response.isSuccess());
        verify(pacsResultMapper, never()).deactivateViewerState(any(PacsViewerStateRequest.class), any());
    }

    @Test
    void editingDoctorCanDeleteScopedViewerState() throws Exception {
        when(request.getHeader(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "X-PACS-VIEWER-ACCESS" -> "viewer-token";
            default -> null;
        });
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        PacsViewerStateResponse existing = emptyViewerStateResponse(72L);
        when(pacsResultMapper.findViewerState(any(PacsViewerStateRequest.class))).thenReturn(existing);
        when(pacsResultMapper.deactivateViewerState(any(PacsViewerStateRequest.class), eq(99L)))
                .thenReturn(1);

        var response = service.deleteBrowserViewerState(new PacsViewerStateRequest(), request);

        assertTrue(response.isSuccess());
        verify(pacsResultMapper).deactivateViewerState(any(PacsViewerStateRequest.class), eq(99L));
    }

    @Test
    void editingDoctorCannotOverwriteAnotherDoctorsViewerState() throws Exception {
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        PacsViewerStateResponse existing = emptyViewerStateResponse(73L);
        existing.setCreatedBy(77L);
        when(pacsResultMapper.findViewerState(any(PacsViewerStateRequest.class))).thenReturn(existing);

        var response = service.saveBrowserViewerState(new PacsViewerStateRequest(), request);

        assertFalse(response.isSuccess());
        verify(pacsResultMapper, never()).updateViewerState(any(PacsViewerStateRequest.class), any());
    }

    @Test
    void editingDoctorCannotCreateViewerStateAfterAnotherDoctorCreatedResult() throws Exception {
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        when(pacsResultMapper.findViewerState(any(PacsViewerStateRequest.class))).thenReturn(null);
        when(pacsResultMapper.findByWorklist(any(PacsResultFindByWorklistRequest.class)))
                .thenReturn(resultResponse(81L, 77L));

        var response = service.saveBrowserViewerState(new PacsViewerStateRequest(), request);

        assertFalse(response.isSuccess());
        verify(pacsResultMapper, never()).insertViewerState(any(PacsViewerStateRequest.class), any());
        verify(pacsResultMapper, never()).updateViewerState(any(PacsViewerStateRequest.class), any());
    }

    @Test
    void reportingDoctorCanRecoverLegacyViewerStateCreatedByAnotherDoctor() throws Exception {
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        PacsViewerStateResponse existing = emptyViewerStateResponse(75L);
        existing.setCreatedBy(77L);
        PacsViewerStateResponse saved = emptyViewerStateResponse(75L);
        when(pacsResultMapper.findViewerState(any(PacsViewerStateRequest.class)))
                .thenReturn(existing, saved);
        when(pacsResultMapper.findByWorklist(any(PacsResultFindByWorklistRequest.class)))
                .thenReturn(resultResponse(82L, 99L));

        var response = service.saveBrowserViewerState(new PacsViewerStateRequest(), request);

        assertTrue(response.isSuccess());
        verify(pacsResultMapper).updateViewerState(any(PacsViewerStateRequest.class), eq(99L));
    }

    @Test
    void editingDoctorCannotCreateResultAfterAnotherDoctorCreatedViewerState() throws Exception {
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        when(worklistMapper.findWorklistById(1L, 7L)).thenReturn(worklistContext());
        when(modalityMapper.countActiveModalitiesByIds(List.of(3L))).thenReturn(1L);
        when(modalityMapper.countActiveHospitalModality(1L, 3L)).thenReturn(1L);
        when(pacsResultMapper.findExisting(any(PacsResultSaveRequest.class))).thenReturn(null);
        PacsViewerStateResponse existingState = emptyViewerStateResponse(76L);
        existingState.setCreatedBy(77L);
        when(pacsResultMapper.findViewerState(any(PacsViewerStateRequest.class))).thenReturn(existingState);

        var response = service.create(saveRequest(), List.of(), request);

        assertFalse(response.isSuccess());
        verify(pacsResultMapper, never()).insertResult(any(PacsResultSaveRequest.class), anyString(), any());
    }

    @Test
    void editingDoctorCannotDeleteAnotherDoctorsViewerState() throws Exception {
        when(viewerAccessKeyService.decode("viewer-token"))
                .thenReturn(claims(ViewerAccessKeyService.ACCESS_EDIT, 99L));

        PacsViewerStateResponse existing = emptyViewerStateResponse(74L);
        existing.setCreatedBy(77L);
        when(pacsResultMapper.findViewerState(any(PacsViewerStateRequest.class))).thenReturn(existing);

        var response = service.deleteBrowserViewerState(new PacsViewerStateRequest(), request);

        assertFalse(response.isSuccess());
        verify(pacsResultMapper, never()).deactivateViewerState(any(PacsViewerStateRequest.class), any());
    }

    private static ViewerAccessClaims claims(String accessMode, Long userId) {
        return new ViewerAccessClaims(
                1L,
                7L,
                22L,
                3L,
                "1.2.3",
                userId,
                userId == null ? null : "doctor",
                accessMode
        );
    }

    private static PacsResultSaveRequest saveRequest() {
        PacsResultSaveRequest request = new PacsResultSaveRequest();
        request.setHospitalId(1L);
        request.setWorklistId(7L);
        request.setStudyId(22L);
        request.setModalityId(3L);
        request.setStudyInstanceUid("1.2.3");
        request.setResultText("Normal");
        return request;
    }

    private static WorklistDetailRow worklistContext() {
        WorklistDetailRow Worklist = new WorklistDetailRow();
        Worklist.setId(7L);
        Worklist.setHospitalId(1L);
        Worklist.setModalityId(3L);
        Worklist.setStudyId(22L);
        Worklist.setPatientId(501L);
        Worklist.setPatientUid("26P0000001");
        Worklist.setPatientName("Soklin Test");
        Worklist.setStudyInstanceUid("1.2.3");
        Worklist.setAccessionNumber("ACC-7");
        return Worklist;
    }

    private static PacsResultResponse resultResponse(Long id, Long createdBy) {
        PacsResultResponse response = new PacsResultResponse();
        response.setId(id);
        response.setHospitalId(1L);
        response.setWorklistId(7L);
        response.setStudyId(22L);
        response.setModalityId(3L);
        response.setStudyInstanceUid("1.2.3");
        response.setCreatedBy(createdBy);
        return response;
    }

    private static PacsViewerStateResponse emptyViewerStateResponse(Long id) {
        PacsViewerStateResponse response = new PacsViewerStateResponse();
        response.setId(id);
        response.setHospitalId(1L);
        response.setWorklistId(7L);
        response.setStudyId(22L);
        response.setModalityId(3L);
        response.setStudyInstanceUid("1.2.3");
        response.setStateType("OHIF_VIEWER_STATE");
        response.setSchemaVersion(2);
        response.setCreatedBy(99L);
        response.setViewerStateJson("{}");
        response.setMeasurementsJson("[]");
        response.setAnnotationsJson("[]");
        response.setSegmentationsJson("[]");
        response.setLabelmapSegmentationsJson("[]");
        response.setContourSegmentationsJson("[]");
        response.setSurfaceSegmentationsJson("[]");
        response.setAdditionalFindingsJson("[]");
        response.setPresentationStateJson("{}");
        response.setToolStateJson("{}");
        response.setMetadataJson("{}");
        return response;
    }

    private static PacsViewerStateChunkRequest chunk(
            String uploadId,
            int chunkIndex,
            int chunkCount,
            byte[] payload,
            String payloadSha256,
            int start,
            int end
    ) {
        PacsViewerStateChunkRequest request = new PacsViewerStateChunkRequest();
        request.setUploadId(uploadId);
        request.setChunkIndex(chunkIndex);
        request.setChunkCount(chunkCount);
        request.setPayloadSizeBytes((long) payload.length);
        request.setPayloadSha256(payloadSha256);
        request.setChunkData(Base64.getEncoder().encodeToString(
                java.util.Arrays.copyOfRange(payload, start, end)
        ));
        return request;
    }

    private static PacsViewerStateChunkCompleteRequest complete(
            String uploadId,
            int chunkCount,
            byte[] payload,
            String payloadSha256
    ) {
        PacsViewerStateChunkCompleteRequest request = new PacsViewerStateChunkCompleteRequest();
        request.setUploadId(uploadId);
        request.setChunkCount(chunkCount);
        request.setPayloadSizeBytes((long) payload.length);
        request.setPayloadSha256(payloadSha256);
        return request;
    }

    private static String sha256(byte[] payload) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
    }

    private static byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
    }
}
