package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.authentication.util.ViewerAccessKeyService;
import com.ut.emrPacs.cache.permission.PermissionCacheService;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.mapper.pacs.PacsResultMapper;
import com.ut.emrPacs.mapper.pacs.StudyMapper;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.dto.request.pacs.study.StudyStatusUpdateRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultResponse;
import com.ut.emrPacs.model.dto.response.pacs.study.StudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.ViewerInfoResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyServiceImplViewerAccessTest {

    @Mock
    private ViewerAccessKeyService viewerAccessKeyService;
    @Mock
    private StudyMapper studyMapper;
    @Mock
    private DicomServerMapper dicomServerMapper;
    @Mock
    private PacsResultMapper pacsResultMapper;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private PermissionCacheService permissionCacheService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void studyViewerTokenIncludesModalityScope() {
        StudyServiceImpl service = new StudyServiceImpl();
        ReflectionTestUtils.setField(service, "viewerAccessKeyService", viewerAccessKeyService);

        StudyResponse study = new StudyResponse();
        study.setHospitalId(1L);
        study.setId(22L);
        study.setModalityId(3L);
        study.setStudyInstanceUid("1.2.3");

        when(viewerAccessKeyService.issue(
                1L,
                null,
                22L,
                3L,
                "1.2.3",
                null,
                null,
                ViewerAccessKeyService.ACCESS_EDIT
        )).thenReturn("viewer-token");

        String token = ReflectionTestUtils.invokeMethod(
                service,
                "issueViewerApiKey",
                study,
                ViewerAccessKeyService.ACCESS_EDIT
        );

        assertEquals("viewer-token", token);
        verify(viewerAccessKeyService).issue(
                1L,
                null,
                22L,
                3L,
                "1.2.3",
                null,
                null,
                ViewerAccessKeyService.ACCESS_EDIT
        );
    }

    @Test
    void studyViewerInfoIncludesPublicKeysNeededForResultSave() throws Exception {
        StudyServiceImpl service = new StudyServiceImpl();
        ReflectionTestUtils.setField(service, "studyMapper", studyMapper);
        ReflectionTestUtils.setField(service, "dicomServerMapper", dicomServerMapper);
        ReflectionTestUtils.setField(service, "pacsResultMapper", pacsResultMapper);
        ReflectionTestUtils.setField(service, "viewerAccessKeyService", viewerAccessKeyService);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());
        ReflectionTestUtils.setField(service, "activityLogService", activityLogService);

        var auth = new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        auth.setDetails(new CurrentUserPrincipal(99L, "admin", 11L, "HSP001", "pacs-web", "jti", 1L));
        SecurityContextHolder.getContext().setAuthentication(auth);

        StudyResponse study = new StudyResponse();
        study.setId(22L);
        study.setPublicKey("study-public-key");
        study.setHospitalId(11L);
        study.setHospitalPublicKey("hospital-public-key");
        study.setPatientId(44L);
        study.setPatientPublicKey("patient-public-key");
        study.setPatientName("HEL SOK");
        study.setMrn("26-H001-P0000003");
        study.setPatientHn("23-014677");
        study.setModalityId(3L);
        study.setModalityPublicKey("modality-public-key");
        study.setModality("CT");
        study.setStudyInstanceUid("1.2.3");
        study.setAccessionNumber("123");
        study.setInstitutionName("TSNH HOSPITAL");

        HospitalDicomServerResponse server = new HospitalDicomServerResponse();
        server.setViewerBaseUrl("http://localhost:3005");

        when(studyMapper.findById(11L, 22L)).thenReturn(study);
        when(dicomServerMapper.findPrimaryActiveDicomServerByHospital(11L)).thenReturn(server);
        when(viewerAccessKeyService.issue(
                11L,
                null,
                22L,
                3L,
                "1.2.3",
                99L,
                "admin",
                ViewerAccessKeyService.ACCESS_EDIT
        )).thenReturn("viewer-token");

        var response = service.getViewerInfo(22L, null, "basic", ViewerAccessKeyService.ACCESS_EDIT, null);

        assertTrue(response.isSuccess());
        ViewerInfoResponse viewerInfo = (ViewerInfoResponse) response.getBody().getData().getFirst();
        assertEquals("hospital-public-key", viewerInfo.getHospitalPublicKey());
        assertEquals("study-public-key", viewerInfo.getStudyPublicKey());
        assertEquals("modality-public-key", viewerInfo.getModalityPublicKey());
        assertEquals("patient-public-key", viewerInfo.getPatientPublicKey());
        assertEquals("23-014677", viewerInfo.getPatientHn());
        assertEquals("TSNH HOSPITAL", viewerInfo.getInstitutionName());
        assertEquals(Boolean.TRUE, viewerInfo.getCanEditResult());
        assertEquals(Boolean.TRUE, viewerInfo.getCanEditViewerState());
        assertTrue(viewerInfo.getViewerUrl().contains("canEditResult=1"));
        assertTrue(viewerInfo.getViewerUrl().contains("canEditViewerState=1"));

        verify(activityLogService).insert(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.eq("Study"),
                ArgumentMatchers.eq("Study (Viewer Info)"),
                ArgumentMatchers.eq("View"),
                ArgumentMatchers.eq(1),
                ArgumentMatchers.eq("Success"),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.isNull()
        );
    }

    @Test
    void completedStudyViewerInfoIsReadOnly() throws Exception {
        StudyServiceImpl service = new StudyServiceImpl();
        ReflectionTestUtils.setField(service, "studyMapper", studyMapper);
        ReflectionTestUtils.setField(service, "dicomServerMapper", dicomServerMapper);
        ReflectionTestUtils.setField(service, "pacsResultMapper", pacsResultMapper);
        ReflectionTestUtils.setField(service, "viewerAccessKeyService", viewerAccessKeyService);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());
        ReflectionTestUtils.setField(service, "activityLogService", activityLogService);

        var auth = new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        auth.setDetails(new CurrentUserPrincipal(99L, "admin", 11L, "HSP001", "pacs-web", "jti", 1L));
        SecurityContextHolder.getContext().setAuthentication(auth);

        StudyResponse study = new StudyResponse();
        study.setId(22L);
        study.setPublicKey("study-public-key");
        study.setHospitalId(11L);
        study.setHospitalPublicKey("hospital-public-key");
        study.setModalityId(3L);
        study.setModalityPublicKey("modality-public-key");
        study.setModality("CT");
        study.setStudyInstanceUid("1.2.3");

        PacsResultResponse completed = new PacsResultResponse();
        completed.setId(88L);
        completed.setHospitalId(11L);
        completed.setStudyId(22L);
        completed.setModalityId(3L);
        completed.setStudyInstanceUid("1.2.3");
        completed.setCreatedBy(99L);
        completed.setCompleted(Boolean.TRUE);
        completed.setStatus("COMPLETED");

        HospitalDicomServerResponse server = new HospitalDicomServerResponse();
        server.setViewerBaseUrl("http://localhost:3005");

        when(studyMapper.findById(11L, 22L)).thenReturn(study);
        when(dicomServerMapper.findPrimaryActiveDicomServerByHospital(11L)).thenReturn(server);
        when(pacsResultMapper.findByStudyId(11L, 3L, 22L)).thenReturn(completed);
        when(viewerAccessKeyService.issue(
                11L,
                null,
                22L,
                3L,
                "1.2.3",
                99L,
                "admin",
                ViewerAccessKeyService.ACCESS_EDIT
        )).thenReturn("viewer-token");

        var response = service.getViewerInfo(22L, null, "basic", ViewerAccessKeyService.ACCESS_EDIT, null);

        assertTrue(response.isSuccess());
        ViewerInfoResponse viewerInfo = (ViewerInfoResponse) response.getBody().getData().getFirst();
        assertEquals(Boolean.FALSE, viewerInfo.getCanEditResult());
        assertEquals(Boolean.FALSE, viewerInfo.getCanEditViewerState());
        assertTrue(viewerInfo.getViewerUrl().contains("canEditResult=0"));
        assertTrue(viewerInfo.getViewerUrl().contains("canEditViewerState=0"));
    }

    @Test
    void adminCanReopenCompletedStudyFromStudyList() throws Exception {
        StudyServiceImpl service = new StudyServiceImpl();
        ReflectionTestUtils.setField(service, "studyMapper", studyMapper);
        ReflectionTestUtils.setField(service, "pacsResultMapper", pacsResultMapper);
        ReflectionTestUtils.setField(service, "permissionCacheService", permissionCacheService);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());
        ReflectionTestUtils.setField(service, "activityLogService", activityLogService);

        var auth = new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        auth.setDetails(new CurrentUserPrincipal(99L, "admin", 11L, "HSP001", "pacs-web", "jti", 1L));
        SecurityContextHolder.getContext().setAuthentication(auth);

        StudyResponse study = new StudyResponse();
        study.setId(22L);
        study.setPublicKey("study-public-key");
        study.setHospitalId(11L);
        study.setModalityId(3L);
        study.setStudyInstanceUid("1.2.3");
        study.setStatus("COMPLETED");

        PacsResultResponse result = new PacsResultResponse();
        result.setId(88L);
        result.setHospitalId(11L);
        result.setStudyId(22L);
        result.setModalityId(3L);
        result.setCompleted(Boolean.TRUE);
        result.setStatus("FINAL");

        when(studyMapper.findById(11L, 22L)).thenReturn(study);
        when(studyMapper.updateStatusById(11L, 22L, 1)).thenReturn(1);
        when(pacsResultMapper.findByStudyId(11L, 3L, 22L)).thenReturn(result);
        when(permissionCacheService.getPermissionCodes(99L, 11L, 1L)).thenReturn(Set.of("pacs.study.status_update"));

        StudyStatusUpdateRequest request = new StudyStatusUpdateRequest();
        request.setStatus("IMAGE_RECEIVED");

        var response = service.updateStatus(22L, request, null);

        assertTrue(response.isSuccess());
        verify(studyMapper).updateStatusById(11L, 22L, 1);
        verify(pacsResultMapper).updateResultStatusById(88L, "IMAGE_RECEIVED", false);
    }

    @Test
    void nonAdminCannotUpdateStudyStatus() throws Exception {
        StudyServiceImpl service = new StudyServiceImpl();
        ReflectionTestUtils.setField(service, "studyMapper", studyMapper);
        ReflectionTestUtils.setField(service, "pacsResultMapper", pacsResultMapper);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());
        ReflectionTestUtils.setField(service, "activityLogService", activityLogService);

        var auth = new UsernamePasswordAuthenticationToken(
                "doctor",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))
        );
        auth.setDetails(new CurrentUserPrincipal(99L, "doctor", 11L, "HSP001", "pacs-web", "jti", 1L));
        SecurityContextHolder.getContext().setAuthentication(auth);

        StudyStatusUpdateRequest request = new StudyStatusUpdateRequest();
        request.setStatus("IMAGE_RECEIVED");

        var response = service.updateStatus(22L, request, null);

        assertEquals(403, response.getHeader().getStatusCode());
        verify(studyMapper, never()).updateStatusById(11L, 22L, 1);
        verify(pacsResultMapper, never()).updateResultStatusById(88L, "IMAGE_RECEIVED", false);
    }
}
