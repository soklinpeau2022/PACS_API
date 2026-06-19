package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.authentication.util.JwtTokenService;
import com.ut.emrPacs.authentication.util.PublicViewerAttemptGuard;
import com.ut.emrPacs.authentication.util.ViewerAccessKeyService;
import com.ut.emrPacs.authentication.util.ViewerAccessKeyService.ViewerAccessClaims;
import com.ut.emrPacs.config.WorklistConstants;
import com.ut.emrPacs.mapper.hospital.HospitalMapper;
import com.ut.emrPacs.mapper.modality.ModalityMapper;
import com.ut.emrPacs.mapper.auth.RevokedTokenMapper;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.mapper.pacs.PacsResultMapper;
import com.ut.emrPacs.mapper.pacs.PatientMapper;
import com.ut.emrPacs.mapper.pacs.StudyMapper;
import com.ut.emrPacs.mapper.pacs.WorklistMapper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistActionRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistAssignRequest;
import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerWorklistCreateRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistSendToPacsRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistUpdateRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistViewStudyRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.PublicViewerAuthorizeRequest;
import com.ut.emrPacs.model.dto.response.authentication.token.AccessTokenResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalModalityServerRouteResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerStudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistCreateResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultResponse;
import com.ut.emrPacs.model.dto.response.pacs.patient.PatientResponse;
import com.ut.emrPacs.model.dto.response.pacs.study.StudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.ViewerInfoResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistViewerStudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import com.ut.emrPacs.model.dto.response.systemSettings.hospital.HospitalResponseDetail;
import com.ut.emrPacs.model.dto.response.systemSettings.modality.ModalityResponse;
import com.ut.emrPacs.model.enums.WorklistStatus;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DicomServerClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorklistServiceImplWorklistCrudTest {

    @Mock
    private WorklistMapper WorklistMapper;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private ModalityMapper modalityMapper;
    @Mock
    private HospitalMapper hospitalMapper;
    @Mock
    private DicomServerMapper dicomServerMapper;
    @Mock
    private PacsResultMapper pacsResultMapper;
    @Mock
    private StudyMapper studyMapper;
    @Mock
    private DicomServerClientService dicomServerClientService;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private ViewerAccessKeyService viewerAccessKeyService;
    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private RevokedTokenMapper revokedTokenMapper;
    @Mock
    private PublicEntityKeyResolver publicEntityKeyResolver;
    @Mock
    private PublicViewerAttemptGuard publicViewerAttemptGuard;

    private WorklistServiceImpl WorklistService;

    @BeforeEach
    void setUp() {
        WorklistService = new WorklistServiceImpl();
        ReflectionTestUtils.setField(WorklistService, "WorklistMapper", WorklistMapper);
        ReflectionTestUtils.setField(WorklistService, "messageService", new MessageService());
        ReflectionTestUtils.setField(WorklistService, "activityLogService", activityLogService);
        ReflectionTestUtils.setField(WorklistService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(WorklistService, "modalityMapper", modalityMapper);
        ReflectionTestUtils.setField(WorklistService, "hospitalMapper", hospitalMapper);
        ReflectionTestUtils.setField(WorklistService, "dicomServerMapper", dicomServerMapper);
        ReflectionTestUtils.setField(WorklistService, "pacsResultMapper", pacsResultMapper);
        ReflectionTestUtils.setField(WorklistService, "studyMapper", studyMapper);
        ReflectionTestUtils.setField(WorklistService, "dicomServerClientService", dicomServerClientService);
        ReflectionTestUtils.setField(WorklistService, "jwtTokenService", jwtTokenService);
        ReflectionTestUtils.setField(WorklistService, "viewerAccessKeyService", viewerAccessKeyService);
        ReflectionTestUtils.setField(WorklistService, "jwtDecoder", jwtDecoder);
        ReflectionTestUtils.setField(WorklistService, "revokedTokenMapper", revokedTokenMapper);
        ReflectionTestUtils.setField(WorklistService, "publicEntityKeyResolver", publicEntityKeyResolver);
        ReflectionTestUtils.setField(WorklistService, "publicViewerAttemptGuard", publicViewerAttemptGuard);
        ReflectionTestUtils.setField(WorklistService, "viewerDicomwebTokenMs", 86_400_000L);
        lenient().when(publicEntityKeyResolver.resolve(any(PublicEntityKeyResolver.Entity.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "n/a", "ROLE_ADMIN");
        auth.setAuthenticated(true);
        auth.setDetails(new CurrentUserPrincipal(99L, "admin", 11L, "HSP001", "pacs-web", "jti-1", 1L));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void authorizeViewerDicomWebShouldGrantOnlyBoundStudyRequests() {
        Jwt jwt = Jwt.withTokenValue("viewer-token")
                .header("alg", "none")
                .claim("clientId", "pacs-viewer-dicomweb")
                .claim("scope", "pacs.viewer.dicomweb")
                .claim("hospitalId", 11L)
                .claim("worklistId", 1660L)
                .claim("studyInstanceUid", "1.2.840.113619.102201.1660")
                .build();
        when(jwtDecoder.decode("viewer-token")).thenReturn(jwt);

        var granted = WorklistService.authorizeViewerDicomWeb(Map.of(
                "token-value", "Bearer viewer-token",
                "method", "get",
                "uri", "/dicom-web/studies/1.2.840.113619.102201.1660/series/1/instances/2/frames/1",
                "level", "series",
                "dicom-uid", "1.2.840.113619.102201.series"
        ));
        var denied = WorklistService.authorizeViewerDicomWeb(Map.of(
                "token-value", "Bearer viewer-token",
                "method", "get",
                "uri", "/dicom-web/studies/1.2.840.113619.102201.9999/series",
                "StudyInstanceUID", "1.2.840.113619.102201.9999"
        ));
        var grantedStudyFind = WorklistService.authorizeViewerDicomWeb(Map.of(
                "token-value", "Bearer viewer-token",
                "method", "post",
                "uri", "/tools/find",
                "request", Map.of(
                        "Level", "Study",
                        "Query", Map.of("StudyInstanceUID", "1.2.840.113619.102201.1660")
                )
        ));
        var grantedStudyFindResourceCheck = WorklistService.authorizeViewerDicomWeb(Map.of(
                "token-value", "Bearer viewer-token",
                "method", "post",
                "level", "study",
                "dicom-uid", "1.2.840.113619.102201.1660"
        ));
        var deniedUnboundFind = WorklistService.authorizeViewerDicomWeb(Map.of(
                "token-value", "Bearer viewer-token",
                "method", "post",
                "uri", "/tools/find",
                "request", Map.of(
                        "Level", "Study",
                        "Query", Map.of("PatientName", "*")
                )
        ));

        assertEquals(Boolean.TRUE, granted.getBody().get("granted"));
        assertEquals(Boolean.TRUE, grantedStudyFind.getBody().get("granted"));
        assertEquals(Boolean.TRUE, grantedStudyFindResourceCheck.getBody().get("granted"));
        assertEquals(Boolean.FALSE, denied.getBody().get("granted"));
        assertEquals(Boolean.FALSE, deniedUnboundFind.getBody().get("granted"));
    }

    @Test
    void authorizeViewerDicomWebProxyShouldAllowOnlyBoundStudyRequests() {
        Jwt jwt = Jwt.withTokenValue("viewer-token")
                .header("alg", "none")
                .claim("clientId", "pacs-viewer-dicomweb")
                .claim("scope", "pacs.viewer.dicomweb")
                .claim("hospitalId", 11L)
                .claim("worklistId", 1660L)
                .claim("studyInstanceUid", "1.2.840.113619.102201.1660")
                .build();
        when(jwtDecoder.decode("viewer-token")).thenReturn(jwt);

        MockHttpServletRequest granted = new MockHttpServletRequest("GET", "/pacsApi/worklist/viewer-dicom-web-proxy-authorize");
        granted.addHeader("X-Original-URI", "/pacs-dicomweb/studies/1.2.840.113619.102201.1660/series");
        granted.addHeader("X-Original-Method", "GET");
        granted.addHeader("X-PACS-DICOMWEB-TOKEN", "viewer-token");

        MockHttpServletRequest grantedStudyQuery = new MockHttpServletRequest("GET", "/pacsApi/worklist/viewer-dicom-web-proxy-authorize");
        grantedStudyQuery.addHeader("X-Original-URI", "/pacs-dicomweb/studies?StudyInstanceUID=1.2.840.113619.102201.1660");
        grantedStudyQuery.addHeader("X-Original-Method", "GET");
        grantedStudyQuery.addHeader("X-PACS-DICOMWEB-TOKEN", "viewer-token");

        MockHttpServletRequest deniedWideQuery = new MockHttpServletRequest("GET", "/pacsApi/worklist/viewer-dicom-web-proxy-authorize");
        deniedWideQuery.addHeader("X-Original-URI", "/pacs-dicomweb/studies");
        deniedWideQuery.addHeader("X-Original-Method", "GET");
        deniedWideQuery.addHeader("X-PACS-DICOMWEB-TOKEN", "viewer-token");

        MockHttpServletRequest deniedWrongStudy = new MockHttpServletRequest("GET", "/pacsApi/worklist/viewer-dicom-web-proxy-authorize");
        deniedWrongStudy.addHeader("X-Original-URI", "/pacs-dicomweb/studies/1.2.840.113619.102201.9999/series");
        deniedWrongStudy.addHeader("X-Original-Method", "GET");
        deniedWrongStudy.addHeader("X-PACS-DICOMWEB-TOKEN", "viewer-token");

        MockHttpServletRequest deniedQueryTokenOnly = new MockHttpServletRequest("GET", "/pacsApi/worklist/viewer-dicom-web-proxy-authorize");
        deniedQueryTokenOnly.addHeader("X-Original-URI", "/pacs-dicomweb/studies/1.2.840.113619.102201.1660/series?token=viewer-token");
        deniedQueryTokenOnly.addHeader("X-Original-Method", "GET");

        assertEquals(HttpStatus.NO_CONTENT.value(), WorklistService.authorizeViewerDicomWebProxy(granted).getStatusCode().value());
        assertEquals(HttpStatus.NO_CONTENT.value(), WorklistService.authorizeViewerDicomWebProxy(grantedStudyQuery).getStatusCode().value());
        assertEquals(HttpStatus.FORBIDDEN.value(), WorklistService.authorizeViewerDicomWebProxy(deniedWideQuery).getStatusCode().value());
        assertEquals(HttpStatus.FORBIDDEN.value(), WorklistService.authorizeViewerDicomWebProxy(deniedWrongStudy).getStatusCode().value());
        assertEquals(HttpStatus.FORBIDDEN.value(), WorklistService.authorizeViewerDicomWebProxy(deniedQueryTokenOnly).getStatusCode().value());
    }

    @Test
    void renewViewerDicomWebShouldIssueFreshScopedToken() {
        Jwt jwt = Jwt.withTokenValue("viewer-token")
                .header("alg", "none")
                .claim("clientId", "pacs-viewer-dicomweb")
                .claim("scope", "pacs.viewer.dicomweb")
                .claim("hospitalId", 11L)
                .claim("worklistId", 1660L)
                .claim("studyInstanceUid", "1.2.840.113619.102201.1660")
                .claim("jti", "viewer-jti-1")
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        when(jwtDecoder.decode("viewer-token")).thenReturn(jwt);
        when(revokedTokenMapper.countByJti("viewer-jti-1")).thenReturn(0L);
        when(jwtTokenService.issueViewerDicomwebToken(
                eq(11L),
                eq(1660L),
                eq(null),
                eq("1.2.840.113619.102201.1660"),
                anyLong()
        )).thenReturn(new AccessTokenResponse("Bearer", "viewer-token-2", null, 1800L, "pacs.viewer.dicomweb"));

        var response = WorklistService.renewViewerDicomWeb(Map.of("token", "viewer-token"));

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertEquals(Boolean.TRUE, response.getBody().get("success"));
        assertEquals("viewer-token-2", response.getBody().get("token"));
        assertEquals(86_400L, response.getBody().get("expiresInSeconds"));
    }

    @Test
    void revokeViewerDicomWebShouldStoreTokenJtiInRevocationList() {
        Instant expiresAt = Instant.now().plusSeconds(1800);
        Jwt jwt = Jwt.withTokenValue("viewer-token")
                .header("alg", "none")
                .claim("clientId", "pacs-viewer-dicomweb")
                .claim("scope", "pacs.viewer.dicomweb")
                .claim("hospitalId", 11L)
                .claim("worklistId", 1660L)
                .claim("studyInstanceUid", "1.2.840.113619.102201.1660")
                .claim("jti", "viewer-jti-1")
                .expiresAt(expiresAt)
                .build();
        when(jwtDecoder.decode("viewer-token")).thenReturn(jwt);
        when(revokedTokenMapper.countByJti("viewer-jti-1")).thenReturn(0L);

        var response = WorklistService.revokeViewerDicomWeb(Map.of("token", "viewer-token"));

        assertEquals(HttpStatus.NO_CONTENT.value(), response.getStatusCode().value());
        verify(revokedTokenMapper).revokeToken(eq("viewer-jti-1"), eq(99L), any());
    }

    @Test
    void revokedViewerDicomWebTokenShouldBeRejected() {
        Jwt jwt = Jwt.withTokenValue("viewer-token")
                .header("alg", "none")
                .claim("clientId", "pacs-viewer-dicomweb")
                .claim("scope", "pacs.viewer.dicomweb")
                .claim("hospitalId", 11L)
                .claim("worklistId", 1660L)
                .claim("studyInstanceUid", "1.2.840.113619.102201.1660")
                .claim("jti", "viewer-jti-1")
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        when(jwtDecoder.decode("viewer-token")).thenReturn(jwt);
        when(revokedTokenMapper.countByJti("viewer-jti-1")).thenReturn(1L);

        var response = WorklistService.renewViewerDicomWeb(Map.of("token", "viewer-token"));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        verify(jwtTokenService, never()).issueViewerDicomwebToken(anyLong(), anyLong(), any(), anyString(), anyLong());
    }

    @Test
    void renewViewerDicomWebShouldRecoverFromRevokedReloadTokenWithViewerAccessKey() {
        Jwt jwt = Jwt.withTokenValue("viewer-token")
                .header("alg", "none")
                .claim("clientId", "pacs-viewer-dicomweb")
                .claim("scope", "pacs.viewer.dicomweb")
                .claim("hospitalId", 11L)
                .claim("worklistId", 1660L)
                .claim("studyInstanceUid", "1.2.840.113619.102201.1660")
                .claim("jti", "viewer-jti-1")
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        when(jwtDecoder.decode("viewer-token")).thenReturn(jwt);
        when(revokedTokenMapper.countByJti("viewer-jti-1")).thenReturn(1L);
        when(viewerAccessKeyService.decode("viewer-access-token")).thenReturn(new ViewerAccessClaims(
                11L,
                1660L,
                44L,
                4L,
                "1.2.840.113619.102201.1660",
                99L,
                "admin",
                ViewerAccessKeyService.ACCESS_READ
        ));
        when(jwtTokenService.issueViewerDicomwebToken(
                eq(11L),
                eq(1660L),
                eq(44L),
                eq("1.2.840.113619.102201.1660"),
                anyLong()
        )).thenReturn(new AccessTokenResponse("Bearer", "viewer-token-3", null, 600L, "pacs.viewer.dicomweb"));
        when(viewerAccessKeyService.issue(
                eq(11L),
                eq(1660L),
                eq(44L),
                eq(4L),
                eq("1.2.840.113619.102201.1660"),
                eq(99L),
                eq("admin"),
                eq(ViewerAccessKeyService.ACCESS_READ)
        )).thenReturn("viewer-access-token-2");

        var response = WorklistService.renewViewerDicomWeb(Map.of(
                "token", "viewer-token",
                "viewerAccessToken", "viewer-access-token"
        ));

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertEquals(Boolean.TRUE, response.getBody().get("success"));
        assertEquals("viewer-token-3", response.getBody().get("token"));
        assertEquals("viewer-access-token-2", response.getBody().get("viewerAccessToken"));
        assertEquals(ViewerAccessKeyService.ACCESS_READ, response.getBody().get("viewerAccess"));
        assertEquals(Boolean.FALSE, response.getBody().get("canEditResult"));
        assertEquals(Boolean.FALSE, response.getBody().get("canEditViewerState"));
    }

    @Test
    void renewViewerDicomWebShouldKeepStudyScopedEditAccessAfterRefresh() {
        Jwt jwt = Jwt.withTokenValue("viewer-token")
                .header("alg", "none")
                .claim("clientId", "pacs-viewer-dicomweb")
                .claim("scope", "pacs.viewer.dicomweb")
                .claim("hospitalId", 11L)
                .claim("studyId", 44L)
                .claim("studyInstanceUid", "1.2.840.113619.102201.1660")
                .claim("jti", "viewer-jti-1")
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        when(jwtDecoder.decode("viewer-token")).thenReturn(jwt);
        when(revokedTokenMapper.countByJti("viewer-jti-1")).thenReturn(1L);
        when(viewerAccessKeyService.decode("viewer-access-token")).thenReturn(new ViewerAccessClaims(
                11L,
                null,
                44L,
                4L,
                "1.2.840.113619.102201.1660",
                99L,
                "admin",
                ViewerAccessKeyService.ACCESS_EDIT
        ));

        StudyResponse study = new StudyResponse();
        study.setHospitalId(11L);
        study.setId(44L);
        study.setModalityId(4L);
        study.setStudyInstanceUid("1.2.840.113619.102201.1660");
        when(studyMapper.findById(11L, 44L)).thenReturn(study);
        when(jwtTokenService.issueViewerDicomwebToken(
                eq(11L),
                eq(null),
                eq(44L),
                eq("1.2.840.113619.102201.1660"),
                anyLong()
        )).thenReturn(new AccessTokenResponse("Bearer", "viewer-token-4", null, 600L, "pacs.viewer.dicomweb"));
        when(viewerAccessKeyService.issue(
                eq(11L),
                eq(null),
                eq(44L),
                eq(4L),
                eq("1.2.840.113619.102201.1660"),
                eq(99L),
                eq("admin"),
                eq(ViewerAccessKeyService.ACCESS_EDIT)
        )).thenReturn("viewer-access-token-2");

        var response = WorklistService.renewViewerDicomWeb(Map.of(
                "token", "viewer-token",
                "viewerAccessToken", "viewer-access-token"
        ));

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertEquals(Boolean.TRUE, response.getBody().get("success"));
        assertEquals("viewer-token-4", response.getBody().get("token"));
        assertEquals("viewer-access-token-2", response.getBody().get("viewerAccessToken"));
        assertEquals(ViewerAccessKeyService.ACCESS_EDIT, response.getBody().get("viewerAccess"));
        assertEquals(Boolean.TRUE, response.getBody().get("canEditResult"));
        assertEquals(Boolean.TRUE, response.getBody().get("canEditViewerState"));
    }

    @Test
    void renewViewerDicomWebShouldLockCompletedStudyScopedEditAccessAfterRefresh() {
        Jwt jwt = Jwt.withTokenValue("viewer-token")
                .header("alg", "none")
                .claim("clientId", "pacs-viewer-dicomweb")
                .claim("scope", "pacs.viewer.dicomweb")
                .claim("hospitalId", 11L)
                .claim("studyId", 44L)
                .claim("studyInstanceUid", "1.2.840.113619.102201.1660")
                .claim("jti", "viewer-jti-1")
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        when(jwtDecoder.decode("viewer-token")).thenReturn(jwt);
        when(revokedTokenMapper.countByJti("viewer-jti-1")).thenReturn(1L);
        when(viewerAccessKeyService.decode("viewer-access-token")).thenReturn(new ViewerAccessClaims(
                11L,
                null,
                44L,
                4L,
                "1.2.840.113619.102201.1660",
                99L,
                "admin",
                ViewerAccessKeyService.ACCESS_EDIT
        ));

        StudyResponse study = new StudyResponse();
        study.setHospitalId(11L);
        study.setId(44L);
        study.setModalityId(4L);
        study.setStudyInstanceUid("1.2.840.113619.102201.1660");
        when(studyMapper.findById(11L, 44L)).thenReturn(study);
        PacsResultResponse completed = new PacsResultResponse();
        completed.setHospitalId(11L);
        completed.setStudyId(44L);
        completed.setModalityId(4L);
        completed.setStudyInstanceUid("1.2.840.113619.102201.1660");
        completed.setCreatedBy(99L);
        completed.setCompleted(Boolean.TRUE);
        completed.setStatus("COMPLETED");
        when(pacsResultMapper.findByStudyId(11L, 4L, 44L)).thenReturn(completed);
        when(jwtTokenService.issueViewerDicomwebToken(
                eq(11L),
                eq(null),
                eq(44L),
                eq("1.2.840.113619.102201.1660"),
                anyLong()
        )).thenReturn(new AccessTokenResponse("Bearer", "viewer-token-4", null, 600L, "pacs.viewer.dicomweb"));
        when(viewerAccessKeyService.issue(
                eq(11L),
                eq(null),
                eq(44L),
                eq(4L),
                eq("1.2.840.113619.102201.1660"),
                eq(99L),
                eq("admin"),
                eq(ViewerAccessKeyService.ACCESS_EDIT)
        )).thenReturn("viewer-access-token-2");

        var response = WorklistService.renewViewerDicomWeb(Map.of(
                "token", "viewer-token",
                "viewerAccessToken", "viewer-access-token"
        ));

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertEquals(Boolean.TRUE, response.getBody().get("success"));
        assertEquals("viewer-token-4", response.getBody().get("token"));
        assertEquals("viewer-access-token-2", response.getBody().get("viewerAccessToken"));
        assertEquals(ViewerAccessKeyService.ACCESS_EDIT, response.getBody().get("viewerAccess"));
        assertEquals(Boolean.FALSE, response.getBody().get("canEditResult"));
        assertEquals(Boolean.FALSE, response.getBody().get("canEditViewerState"));
    }

    @Test
    void profileViewerDicomWebShouldDenyViewerBearerToken() {
        var response = WorklistService.profileViewerDicomWeb(Map.of("token-value", "viewer-token"));

        assertEquals("UDAYA_DICOM_SERVER Viewer", response.getBody().get("name"));
        assertEquals(List.of(), response.getBody().get("authorized-labels"));
        assertEquals(List.of(), response.getBody().get("permissions"));
        assertEquals(0, response.getBody().get("validity"));
    }

    @Test
    void publicViewerRejectsPhoneThatDoesNotMatchScopedPatient() throws Exception {
        String hospitalKey = "d7fa6fa8-5043-4eac-ae5f-1cccf4e6a6bd";
        String worklistKey = "07e23e31-d02e-49a2-b24c-699ce4c943ec";
        when(publicEntityKeyResolver.resolve(PublicEntityKeyResolver.Entity.HOSPITAL, hospitalKey, null))
                .thenReturn(11L);
        when(publicEntityKeyResolver.resolve(PublicEntityKeyResolver.Entity.WORKLIST, worklistKey, null))
                .thenReturn(1660L);

        WorklistDetailRow worklist = new WorklistDetailRow();
        worklist.setId(1660L);
        worklist.setHospitalId(11L);
        worklist.setPatientId(22L);
        when(WorklistMapper.findWorklistById(11L, 1660L)).thenReturn(worklist);

        PatientResponse patient = new PatientResponse();
        patient.setPhoneNumber("+855 12 345 678");
        when(patientMapper.findById(11L, 22L)).thenReturn(patient);

        PublicViewerAuthorizeRequest request = new PublicViewerAuthorizeRequest();
        request.setHospitalKey(hospitalKey);
        request.setWorklistKey(worklistKey);
        request.setPhoneNumber("012999999");

        var response = WorklistService.authorizePublicViewer(request, new MockHttpServletRequest());

        assertFalse(response.isSuccess());
        assertEquals("Unable to verify viewer access.", response.getHeader().getErrorText());
        verify(publicViewerAttemptGuard).recordFailure(hospitalKey, worklistKey);
        verify(jwtTokenService, never()).issueViewerDicomwebToken(anyLong(), anyLong(), any(), anyString(), anyLong());
    }

    @Test
    void publicViewerRejectsBlockedLinkBeforeDatabaseLookup() throws Exception {
        String hospitalKey = "d7fa6fa8-5043-4eac-ae5f-1cccf4e6a6bd";
        String worklistKey = "07e23e31-d02e-49a2-b24c-699ce4c943ec";
        when(publicViewerAttemptGuard.isBlocked(hospitalKey, worklistKey)).thenReturn(true);

        PublicViewerAuthorizeRequest request = new PublicViewerAuthorizeRequest();
        request.setHospitalKey(hospitalKey);
        request.setWorklistKey(worklistKey);
        request.setPhoneNumber("012345678");

        var response = WorklistService.authorizePublicViewer(request, new MockHttpServletRequest());

        assertFalse(response.isSuccess());
        assertEquals("Unable to verify viewer access.", response.getHeader().getErrorText());
        verifyNoInteractions(publicEntityKeyResolver);
        verifyNoInteractions(WorklistMapper);
        verifyNoInteractions(patientMapper);
    }

    @Test
    void publicViewerAuthorizesMatchingPhoneWithReadOnlyScopedTokens() throws Exception {
        String hospitalKey = "d7fa6fa8-5043-4eac-ae5f-1cccf4e6a6bd";
        String worklistKey = "07e23e31-d02e-49a2-b24c-699ce4c943ec";
        when(publicEntityKeyResolver.resolve(PublicEntityKeyResolver.Entity.HOSPITAL, hospitalKey, null))
                .thenReturn(11L);
        when(publicEntityKeyResolver.resolve(PublicEntityKeyResolver.Entity.WORKLIST, worklistKey, null))
                .thenReturn(1660L);

        WorklistDetailRow worklist = baseWorklist(1660L, WorklistStatus.IN_PROGRESS);
        worklist.setPublicKey(worklistKey);
        worklist.setHospitalPublicKey(hospitalKey);
        worklist.setStudyId(44L);
        worklist.setDicomServerId(4L);
        worklist.setDicomServerStudyId("dicom-server-study-1660");
        worklist.setStudyInstanceUid("1.2.840.113619.102201.1660");
        when(WorklistMapper.findWorklistById(11L, 1660L)).thenReturn(worklist);

        PatientResponse patient = new PatientResponse();
        patient.setPhoneNumber("+855 12 345 678");
        when(patientMapper.findById(11L, 501L)).thenReturn(patient);

        HospitalDicomServerResponse targetServer = new HospitalDicomServerResponse();
        targetServer.setId(4L);
        targetServer.setBaseUrl("http://localhost:8042");
        targetServer.setDicomwebBaseUrl("http://localhost:8042/dicom-web");
        targetServer.setViewerBaseUrl("http://localhost:3005");
        targetServer.setPacsApiCallbackBaseUrl("http://localhost:8080/pacsApi");
        targetServer.setUsername("dicom_server");
        targetServer.setPassword("dicom_server");
        when(dicomServerMapper.findActiveDicomServerByWorklist(11L, 1660L)).thenReturn(targetServer);

        DicomServerStudyResponse studyResponse = new DicomServerStudyResponse();
        studyResponse.setId("dicom-server-study-1660");
        studyResponse.setMainDicomTags(Map.of(
                "StudyInstanceUID", "1.2.840.113619.102201.1660"
        ));
        studyResponse.setInstances(List.of("instance-1", "instance-2"));
        when(dicomServerClientService.getStudyById(
                "http://localhost:8042",
                "dicom_server",
                "dicom_server",
                "dicom-server-study-1660"
        )).thenReturn(studyResponse);
        when(jwtTokenService.issueViewerDicomwebToken(
                eq(11L),
                eq(1660L),
                eq(null),
                eq("1.2.840.113619.102201.1660"),
                anyLong()
        )).thenReturn(new AccessTokenResponse(
                "Bearer",
                "public-dicom-token",
                null,
                1800L,
                "pacs.viewer.dicomweb"
        ));
        when(viewerAccessKeyService.issue(
                eq(11L),
                eq(1660L),
                eq(44L),
                eq(5L),
                eq("1.2.840.113619.102201.1660"),
                eq(null),
                eq(null),
                eq(ViewerAccessKeyService.ACCESS_PUBLIC)
        )).thenReturn("public-viewer-token");

        PublicViewerAuthorizeRequest request = new PublicViewerAuthorizeRequest();
        request.setHospitalKey(hospitalKey);
        request.setWorklistKey(worklistKey);
        request.setPhoneNumber("855-12-345-678");
        request.setMode("segmentation");

        var response = WorklistService.authorizePublicViewer(request, new MockHttpServletRequest());

        assertTrue(response.isSuccess(), response.getHeader() != null ? String.valueOf(response.getHeader().getErrorText()) : "Unknown error");
        ViewerInfoResponse viewerInfo = (ViewerInfoResponse) response.getBody().getData().get(0);
        assertEquals(ViewerAccessKeyService.ACCESS_PUBLIC, viewerInfo.getViewerAccess());
        assertEquals(Boolean.FALSE, viewerInfo.getCanEditResult());
        assertEquals(Boolean.FALSE, viewerInfo.getCanEditViewerState());
        assertEquals("public-dicom-token", viewerInfo.getDicomwebAuthToken());
        assertEquals("public-viewer-token", viewerInfo.getViewerApiKey());
        assertTrue(viewerInfo.getViewerUrl().contains("#token=public-dicom-token"));
        assertTrue(viewerInfo.getViewerUrl().contains("viewerAccessToken=public-viewer-token"));
        assertTrue(viewerInfo.getViewerUrl().contains("canEditResult=0"));
        assertTrue(viewerInfo.getViewerUrl().contains("canEditViewerState=0"));
        verify(publicViewerAttemptGuard).clear(hospitalKey, worklistKey);
    }

    @Test
    void publicViewerAuthorizesMatchingPhoneForDirectStudyWithReadOnlyScopedTokens() throws Exception {
        String hospitalKey = "d7fa6fa8-5043-4eac-ae5f-1cccf4e6a6bd";
        String studyKey = "5509cf78-0aa8-4a79-a32c-58e6d47d40f5";
        when(publicEntityKeyResolver.resolve(PublicEntityKeyResolver.Entity.HOSPITAL, hospitalKey, null))
                .thenReturn(11L);
        when(publicEntityKeyResolver.resolve(PublicEntityKeyResolver.Entity.STUDY, studyKey, null))
                .thenReturn(44L);

        StudyResponse study = new StudyResponse();
        study.setId(44L);
        study.setPublicKey(studyKey);
        study.setHospitalId(11L);
        study.setHospitalPublicKey(hospitalKey);
        study.setPatientId(501L);
        study.setPatientPublicKey("patient-public-key");
        study.setPatientName("HEL SOK");
        study.setMrn("26-H001-P0000003");
        study.setPatientHn("23-014677");
        study.setModalityId(5L);
        study.setModalityPublicKey("modality-public-key");
        study.setModality("CT");
        study.setModalityName("Computed Tomography");
        study.setStatus("IMAGE_RECEIVED");
        study.setStudyInstanceUid("1.2.840.113619.102201.44");
        study.setDicomServerStudyId("dicom-server-study-44");
        study.setInstances(146);
        when(studyMapper.findById(11L, 44L)).thenReturn(study);

        PatientResponse patient = new PatientResponse();
        patient.setPhoneNumber("+855 12 345 678");
        when(patientMapper.findById(11L, 501L)).thenReturn(patient);

        HospitalDicomServerResponse targetServer = new HospitalDicomServerResponse();
        targetServer.setId(4L);
        targetServer.setBaseUrl("http://localhost:8042");
        targetServer.setViewerBaseUrl("http://localhost:3005");
        when(dicomServerMapper.findPrimaryActiveDicomServerByHospital(11L)).thenReturn(targetServer);

        when(jwtTokenService.issueViewerDicomwebToken(
                eq(11L),
                eq(null),
                eq(44L),
                eq("1.2.840.113619.102201.44"),
                anyLong()
        )).thenReturn(new AccessTokenResponse(
                "Bearer",
                "public-study-dicom-token",
                null,
                1800L,
                "pacs.viewer.dicomweb"
        ));
        when(viewerAccessKeyService.issue(
                eq(11L),
                eq(null),
                eq(44L),
                eq(5L),
                eq("1.2.840.113619.102201.44"),
                eq(null),
                eq(null),
                eq(ViewerAccessKeyService.ACCESS_PUBLIC)
        )).thenReturn("public-study-viewer-token");

        PublicViewerAuthorizeRequest request = new PublicViewerAuthorizeRequest();
        request.setHospitalKey(hospitalKey);
        request.setStudyKey(studyKey);
        request.setPhoneNumber("855-12-345-678");
        request.setMode("segmentation");

        var response = WorklistService.authorizePublicViewer(request, new MockHttpServletRequest());

        assertTrue(response.isSuccess(), response.getHeader() != null ? String.valueOf(response.getHeader().getErrorText()) : "Unknown error");
        ViewerInfoResponse viewerInfo = (ViewerInfoResponse) response.getBody().getData().get(0);
        assertEquals(ViewerAccessKeyService.ACCESS_PUBLIC, viewerInfo.getViewerAccess());
        assertEquals(Boolean.FALSE, viewerInfo.getCanEditResult());
        assertEquals(Boolean.FALSE, viewerInfo.getCanEditViewerState());
        assertEquals("public-study-dicom-token", viewerInfo.getDicomwebAuthToken());
        assertEquals("public-study-viewer-token", viewerInfo.getViewerApiKey());
        assertTrue(viewerInfo.getPublicViewerUrl().contains("studyKey=" + studyKey));
        assertTrue(viewerInfo.getViewerUrl().contains("#token=public-study-dicom-token"));
        assertTrue(viewerInfo.getViewerUrl().contains("viewerAccessToken=public-study-viewer-token"));
        assertTrue(viewerInfo.getViewerUrl().contains("canEditResult=0"));
        assertTrue(viewerInfo.getViewerUrl().contains("canEditViewerState=0"));
        verify(publicViewerAttemptGuard).clear(hospitalKey, studyKey);
        verify(WorklistMapper, never()).findWorklistById(anyLong(), anyLong());
    }

    @Test
    void authorizeViewerDicomWebShouldGrantConfiguredDicomServerBasicClient() {
        HospitalDicomServerResponse server = new HospitalDicomServerResponse();
        server.setId(4L);
        server.setName("DicomServer KSFH");
        server.setAeTitle("dicom_server");
        server.setUsername("dicom_server");
        server.setPassword("secret");
        when(dicomServerMapper.listActiveDicomServersByHttpUsername("dicom_server")).thenReturn(List.of(server));

        String token = "Basic " + Base64.getEncoder().encodeToString("dicom_server:secret".getBytes(StandardCharsets.UTF_8));
        var response = WorklistService.authorizeViewerDicomWeb(Map.of(
                "token-value", token,
                "server-id", "dicom_server_ksfh"
        ));

        assertEquals(Boolean.TRUE, response.getBody().get("granted"));
        assertEquals(60, response.getBody().get("validity"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void decodeViewerDicomWebShouldExposeOnlyBoundStudyResource() {
        Jwt jwt = Jwt.withTokenValue("viewer-token")
                .header("alg", "none")
                .claim("clientId", "pacs-viewer-dicomweb")
                .claim("scope", "pacs.viewer.dicomweb")
                .claim("hospitalId", 11L)
                .claim("worklistId", 1660L)
                .claim("studyInstanceUid", "1.2.840.113619.102201.1660")
                .build();
        when(jwtDecoder.decode("viewer-token")).thenReturn(jwt);

        var response = WorklistService.decodeViewerDicomWeb(Map.of("token-value", "Bearer viewer-token"));
        List<Map<String, Object>> resources = (List<Map<String, Object>>) response.getBody().get("resources");

        assertEquals("ohif-viewer-publication", response.getBody().get("token-type"));
        assertEquals(1, resources.size());
        assertEquals("study", resources.get(0).get("level"));
        assertEquals("study", resources.get(0).get("Level"));
        assertEquals("1.2.840.113619.102201.1660", resources.get(0).get("dicom-uid"));
        assertEquals("1.2.840.113619.102201.1660", resources.get(0).get("DicomUid"));
        assertFalse(resources.get(0).containsKey("patientName"));
        assertFalse(resources.get(0).containsKey("url"));
    }

    @Test
    void profileViewerDicomWebShouldGrantConfiguredDicomServerHttpClientOnly() {
        HospitalDicomServerResponse server = new HospitalDicomServerResponse();
        server.setId(4L);
        server.setName("DicomServer KSFH");
        server.setAeTitle("dicom_server");
        server.setUsername("dicom_server");
        server.setPassword("secret");
        when(dicomServerMapper.listActiveDicomServersByHttpUsername("dicom_server")).thenReturn(List.of(server));

        String validBasic = "Basic " + Base64.getEncoder().encodeToString("dicom_server:secret".getBytes(StandardCharsets.UTF_8));
        String invalidBasic = "Basic " + Base64.getEncoder().encodeToString("dicom_server:wrong".getBytes(StandardCharsets.UTF_8));

        var granted = WorklistService.profileViewerDicomWeb(Map.of(
                "token-value", validBasic,
                "server-id", "dicom_server_ksfh"
        ));
        var denied = WorklistService.profileViewerDicomWeb(Map.of(
                "token-value", invalidBasic,
                "server-id", "dicom_server_ksfh"
        ));

        assertEquals("UDAYA_DICOM_SERVER Archive API Client", granted.getBody().get("name"));
        assertEquals(List.of("*"), granted.getBody().get("authorized-labels"));
        assertEquals(List.of("all"), granted.getBody().get("permissions"));
        assertEquals(60, granted.getBody().get("validity"));

        assertEquals("UDAYA_DICOM_SERVER Archive API Client", denied.getBody().get("name"));
        assertEquals(List.of(), denied.getBody().get("permissions"));
        assertEquals(0, denied.getBody().get("validity"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findWorklistByIdShouldReturnStoredEmrWorklistWithoutDicomServerSyncWhenInProgress() throws Exception {
        WorklistDetailRow Worklist = baseWorklist(1401L, WorklistStatus.IN_PROGRESS);
        Worklist.setDicomServerWorklistId("wl-1401");
        Worklist.setStudyDescription("Old Study");
        Worklist.setScheduledDate(LocalDate.of(2026, 5, 22));
        Worklist.setScheduledTime(LocalTime.of(8, 0));

        when(WorklistMapper.findWorklistById(11L, 1401L)).thenReturn(Worklist);

        ResponseMessage<BaseResult> response = WorklistService.findWorklistById(1401L, null);

        assertTrue(response.isSuccess());
        verify(dicomServerClientService, never()).getWorklistById(any());
        verify(WorklistMapper, never()).updateWorklistDicomWorklistFieldsById(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void findWorklistByIdShouldNotCallDicomServerWhenWorklistIsWaiting() throws Exception {
        WorklistDetailRow Worklist = baseWorklist(1450L, WorklistStatus.WAITING);
        when(WorklistMapper.findWorklistById(11L, 1450L)).thenReturn(Worklist);

        ResponseMessage<BaseResult> response = WorklistService.findWorklistById(1450L, null);

        assertTrue(response.isSuccess());
        verify(dicomServerClientService, never()).getWorklistById(any());
        verify(WorklistMapper, never()).updateWorklistDicomWorklistFieldsById(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void assignWorklistShouldGenerateHospitalDailyVisitCode() throws Exception {
        WorklistAssignRequest request = new WorklistAssignRequest();
        request.setPatientId(501L);
        request.setModalityId(5L);
        request.setDicomServerId(7L);
        request.setStudyDescription("CT Chest");
        request.setScheduledDate(LocalDate.of(2026, 5, 27));
        request.setScheduledTime(LocalTime.of(9, 0));

        PatientResponse patient = new PatientResponse();
        patient.setId(501L);
        patient.setFirstName("Soklin");
        patient.setLastName("Test");
        when(patientMapper.findById(11L, 501L)).thenReturn(patient);
        when(modalityMapper.countActiveModalitiesByIds(List.of(5L))).thenReturn(1L);

        HospitalModalityServerRouteResponse route = new HospitalModalityServerRouteResponse();
        route.setDicomServerId(7L);
        when(dicomServerMapper.listActiveRoutesByHospitalAndModality(11L, 5L)).thenReturn(List.of(route));
        when(WorklistMapper.countPatientModalityActiveWorklist(11L, 501L, 5L)).thenReturn(0L);

        ModalityResponse modality = new ModalityResponse();
        modality.setAbbr("CT");
        when(modalityMapper.getModalityById(5L)).thenReturn(List.of(modality));

        HospitalResponseDetail hospital = new HospitalResponseDetail();
        hospital.setCode("H001");
        hospital.setAbbr("KSFH HOSPITAL");
        hospital.setHospitalName("KSFH Hospital");
        when(hospitalMapper.getHospitalById(11L)).thenReturn(List.of(hospital));

        String todayToken = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String shortDateToken = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String expectedVisitCode = "CT-KSFH-" + shortDateToken + "-0001";
        when(WorklistMapper.nextVisitSequence(11L, "CT-KSFH-" + todayToken)).thenReturn(1L);
        WorklistDetailRow createdWorklist = baseWorklist(2001L, WorklistStatus.WAITING);
        createdWorklist.setVisitCode(expectedVisitCode);
        when(WorklistMapper.findWorklistByVisitCode(11L, expectedVisitCode)).thenReturn(null, createdWorklist);
        when(WorklistMapper.assignWorklist(eq(11L), eq(99L), eq(expectedVisitCode), eq(request))).thenReturn(true);

        ResponseMessage<BaseResult> response = WorklistService.assignWorklist(request, null);

        assertTrue(response.isSuccess(), response.getHeader() != null ? String.valueOf(response.getHeader().getErrorText()) : "Unknown error");
        verify(WorklistMapper, never()).findWorklistByVisitCodeAnyHospital(expectedVisitCode);
        verify(WorklistMapper).assignWorklist(eq(11L), eq(99L), eq(expectedVisitCode), eq(request));
    }

    @Test
    void patientWorklistLifecycleShouldCreateUpdateSendUpdateAndCancel() throws Exception {
        String todayToken = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String shortDateToken = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String visitCode = "CT-KSFH-" + shortDateToken + "-0001";

        PatientResponse patient = new PatientResponse();
        patient.setId(501L);
        patient.setFirstName("Soklin");
        patient.setLastName("Lifecycle");
        when(patientMapper.findById(11L, 501L)).thenReturn(patient);

        ModalityResponse modality = new ModalityResponse();
        modality.setId(5L);
        modality.setAbbr("CT");
        modality.setName("Computed Tomography");
        when(modalityMapper.countActiveModalitiesByIds(List.of(5L))).thenReturn(1L);
        when(modalityMapper.getModalityById(5L)).thenReturn(List.of(modality));

        HospitalResponseDetail hospital = new HospitalResponseDetail();
        hospital.setCode("H001");
        hospital.setAbbr("KSFH");
        hospital.setHospitalName("KSFH Hospital");
        when(hospitalMapper.getHospitalById(11L)).thenReturn(List.of(hospital));

        HospitalModalityServerRouteResponse route = new HospitalModalityServerRouteResponse();
        route.setId(14L);
        route.setHospitalId(11L);
        route.setModalityId(5L);
        route.setDicomServerId(4L);
        route.setMachineAeTitle("dicom_server");
        route.setMachineName("CT Room 1");
        when(dicomServerMapper.listActiveRoutesByHospitalAndModality(11L, 5L)).thenReturn(List.of(route));

        HospitalDicomServerResponse targetServer = new HospitalDicomServerResponse();
        targetServer.setId(4L);
        targetServer.setHospitalId(11L);
        targetServer.setBaseUrl("http://localhost:8042");
        targetServer.setUsername("dicom_server");
        targetServer.setPassword("dicom_server");
        targetServer.setAeTitle("dicom_server");
        when(dicomServerMapper.getDicomServerById(4L, 11L)).thenReturn(List.of(targetServer));
        when(dicomServerMapper.findActiveDicomServerByWorklist(11L, 2201L)).thenReturn(targetServer);

        WorklistDetailRow createdWaiting = baseWorklist(2201L, WorklistStatus.WAITING);
        createdWaiting.setPublicKey("worklist-public-key");
        createdWaiting.setVisitCode(visitCode);
        createdWaiting.setAccessionNumber(null);
        createdWaiting.setStudyDescription("CT Chest");
        createdWaiting.setScheduledDate(LocalDate.of(2026, 6, 20));
        createdWaiting.setScheduledTime(LocalTime.of(9, 0));

        WorklistDetailRow waitingAfterUpdate = baseWorklist(2201L, WorklistStatus.WAITING);
        waitingAfterUpdate.setPublicKey("worklist-public-key");
        waitingAfterUpdate.setVisitCode(visitCode);
        waitingAfterUpdate.setAccessionNumber(null);
        waitingAfterUpdate.setStudyDescription("CT Abdomen");
        waitingAfterUpdate.setScheduledDate(LocalDate.of(2026, 6, 21));
        waitingAfterUpdate.setScheduledTime(LocalTime.of(10, 15));

        WorklistDetailRow inProgress = baseWorklist(2201L, WorklistStatus.IN_PROGRESS);
        inProgress.setPublicKey("worklist-public-key");
        inProgress.setDicomServerId(4L);
        inProgress.setDicomServerWorklistId("wl-2201");
        inProgress.setDicomServerWorklistPath("/worklists/wl-2201");
        inProgress.setVisitCode(visitCode);
        inProgress.setAccessionNumber(visitCode);
        inProgress.setStudyDescription("CT Abdomen");
        inProgress.setScheduledDate(LocalDate.of(2026, 6, 21));
        inProgress.setScheduledTime(LocalTime.of(10, 15));

        WorklistDetailRow inProgressAfterUpdate = baseWorklist(2201L, WorklistStatus.IN_PROGRESS);
        inProgressAfterUpdate.setPublicKey("worklist-public-key");
        inProgressAfterUpdate.setDicomServerId(4L);
        inProgressAfterUpdate.setDicomServerWorklistId("wl-2201");
        inProgressAfterUpdate.setDicomServerWorklistPath("/worklists/wl-2201");
        inProgressAfterUpdate.setVisitCode(visitCode);
        inProgressAfterUpdate.setAccessionNumber(visitCode);
        inProgressAfterUpdate.setStudyDescription("CT Abdomen Follow-up");
        inProgressAfterUpdate.setScheduledDate(LocalDate.of(2026, 6, 22));
        inProgressAfterUpdate.setScheduledTime(LocalTime.of(11, 30));

        WorklistDetailRow cancelled = baseWorklist(2201L, WorklistStatus.CANCELLED);
        cancelled.setPublicKey("worklist-public-key");
        cancelled.setDicomServerId(4L);
        cancelled.setDicomServerWorklistId("wl-2201");
        cancelled.setDicomServerWorklistPath("/worklists/wl-2201");
        cancelled.setVisitCode(visitCode);
        cancelled.setAccessionNumber(visitCode);

        when(WorklistMapper.countPatientModalityActiveWorklist(11L, 501L, 5L)).thenReturn(0L);
        when(WorklistMapper.nextVisitSequence(11L, "CT-KSFH-" + todayToken)).thenReturn(1L);
        when(WorklistMapper.findWorklistByVisitCode(11L, visitCode)).thenReturn(null, createdWaiting);
        when(WorklistMapper.assignWorklist(eq(11L), eq(99L), eq(visitCode), any(WorklistAssignRequest.class))).thenReturn(true);
        when(WorklistMapper.findWorklistById(11L, 2201L)).thenReturn(
                createdWaiting,
                waitingAfterUpdate,
                waitingAfterUpdate,
                inProgress,
                inProgressAfterUpdate,
                inProgressAfterUpdate,
                cancelled
        );
        when(WorklistMapper.updateWorklistEditableFieldsById(
                eq(11L),
                eq(2201L),
                any(WorklistUpdateRequest.class),
                eq(4L),
                eq("CT Abdomen"),
                eq(LocalDate.of(2026, 6, 21)),
                eq(LocalTime.of(10, 15)),
                eq(99L)
        )).thenReturn(1);

        DicomServerWorklistCreateResponse createResponse = new DicomServerWorklistCreateResponse();
        createResponse.setId("wl-2201");
        createResponse.setPath("/worklists/wl-2201");
        when(dicomServerClientService.postToDicomServerWorklist(
                eq("http://localhost:8042/worklists/create"),
                eq("dicom_server"),
                eq("dicom_server"),
                any(DicomServerWorklistCreateRequest.class)
        )).thenReturn(createResponse);
        when(WorklistMapper.updateWorklistSentToPacsById(
                eq(11L),
                eq(2201L),
                eq(WorklistStatus.IN_PROGRESS.code()),
                eq(4L),
                eq(14L),
                eq(visitCode),
                eq("CT"),
                eq("DICOM_SERVER"),
                eq("CT Abdomen"),
                eq(LocalDate.of(2026, 6, 21)),
                eq(LocalTime.of(10, 15)),
                eq("wl-2201"),
                eq("/worklists/wl-2201"),
                eq(99L)
        )).thenReturn(1);

        DicomServerWorklistResponse remoteUpdate = worklistResponse(
                "wl-2201",
                "/worklists/wl-2201",
                "CT",
                "CT Abdomen Follow-up",
                "20260622",
                "113000"
        );
        when(dicomServerClientService.updateWorklistById(
                eq("http://localhost:8042"),
                eq("dicom_server"),
                eq("dicom_server"),
                eq("wl-2201"),
                any(DicomServerWorklistCreateRequest.class)
        )).thenReturn(remoteUpdate);
        when(WorklistMapper.updateWorklistDicomWorklistFieldsById(
                eq(11L),
                eq(2201L),
                eq(5L),
                eq(visitCode),
                eq("CT"),
                eq("DICOM_SERVER"),
                eq("CT Abdomen Follow-up"),
                eq(LocalDate.of(2026, 6, 22)),
                eq(LocalTime.of(11, 30)),
                eq("wl-2201"),
                eq("/worklists/wl-2201"),
                eq(99L)
        )).thenReturn(1);
        when(WorklistMapper.updateWorklistWorkflowStatusById(11L, 2201L, WorklistStatus.CANCELLED.code(), null, 99L)).thenReturn(1);

        WorklistAssignRequest assignRequest = new WorklistAssignRequest();
        assignRequest.setPatientId(501L);
        assignRequest.setModalityId(5L);
        assignRequest.setDicomServerId(4L);
        assignRequest.setStudyDescription("CT Chest");
        assignRequest.setScheduledDate(LocalDate.of(2026, 6, 20));
        assignRequest.setScheduledTime(LocalTime.of(9, 0));
        assignRequest.setNotes("created after patient registration");

        ResponseMessage<BaseResult> assignResponse = WorklistService.assignWorklist(assignRequest, null);
        assertTrue(assignResponse.isSuccess(), assignResponse.getHeader() != null ? String.valueOf(assignResponse.getHeader().getErrorText()) : "Unknown error");

        WorklistUpdateRequest waitingUpdate = new WorklistUpdateRequest();
        waitingUpdate.setModalityId(5L);
        waitingUpdate.setDicomServerId(4L);
        waitingUpdate.setStudyDescription("CT Abdomen");
        waitingUpdate.setScheduledDate(LocalDate.of(2026, 6, 21));
        waitingUpdate.setScheduledTime(LocalTime.of(10, 15));
        waitingUpdate.setNotes("update waiting worklist");
        ResponseMessage<BaseResult> waitingUpdateResponse = WorklistService.updateWorklist(2201L, waitingUpdate, null);
        assertTrue(waitingUpdateResponse.isSuccess(), waitingUpdateResponse.getHeader() != null ? String.valueOf(waitingUpdateResponse.getHeader().getErrorText()) : "Unknown error");

        WorklistSendToPacsRequest sendRequest = new WorklistSendToPacsRequest();
        sendRequest.setWorklistId(2201L);
        sendRequest.setRouteId(14L);
        ResponseMessage<BaseResult> sendResponse = WorklistService.sendToPacs(sendRequest, null);
        assertTrue(sendResponse.isSuccess(), sendResponse.getHeader() != null ? String.valueOf(sendResponse.getHeader().getErrorText()) : "Unknown error");

        WorklistUpdateRequest pacsUpdate = new WorklistUpdateRequest();
        pacsUpdate.setModalityId(5L);
        pacsUpdate.setStudyDescription("CT Abdomen Follow-up");
        pacsUpdate.setScheduledDate(LocalDate.of(2026, 6, 22));
        pacsUpdate.setScheduledTime(LocalTime.of(11, 30));
        pacsUpdate.setNotes("update already sent worklist");
        ResponseMessage<BaseResult> pacsUpdateResponse = WorklistService.updateWorklist(2201L, pacsUpdate, null);
        assertTrue(pacsUpdateResponse.isSuccess(), pacsUpdateResponse.getHeader() != null ? String.valueOf(pacsUpdateResponse.getHeader().getErrorText()) : "Unknown error");

        WorklistActionRequest cancelRequest = new WorklistActionRequest();
        cancelRequest.setId(2201L);
        cancelRequest.setNotes("cancel after PACS send");
        ResponseMessage<BaseResult> cancelResponse = WorklistService.updateStatus(cancelRequest, WorklistStatus.CANCELLED.name(), null);
        assertTrue(cancelResponse.isSuccess(), cancelResponse.getHeader() != null ? String.valueOf(cancelResponse.getHeader().getErrorText()) : "Unknown error");

        verify(dicomServerClientService).postToDicomServerWorklist(
                eq("http://localhost:8042/worklists/create"),
                eq("dicom_server"),
                eq("dicom_server"),
                argThat(payload -> payload != null
                        && payload.getTags() != null
                        && visitCode.equals(payload.getTags().getAccessionNumber())
                        && "CT Abdomen".equals(payload.getTags().getStudyDescription()))
        );
        verify(dicomServerClientService).updateWorklistById(
                eq("http://localhost:8042"),
                eq("dicom_server"),
                eq("dicom_server"),
                eq("wl-2201"),
                argThat(payload -> payload != null
                        && payload.getTags() != null
                        && "CT Abdomen Follow-up".equals(payload.getTags().getStudyDescription())
                        && payload.getTags().getScheduledProcedureStepSequence() != null
                        && "20260622".equals(payload.getTags().getScheduledProcedureStepSequence().get(0).getScheduledProcedureStepStartDate())
                        && "113000".equals(payload.getTags().getScheduledProcedureStepSequence().get(0).getScheduledProcedureStepStartTime()))
        );
        verify(dicomServerClientService).deleteWorklistById(
                "http://localhost:8042",
                "dicom_server",
                "dicom_server",
                "wl-2201"
        );
        verify(WorklistMapper).updateWorklistWorkflowStatusById(11L, 2201L, WorklistStatus.CANCELLED.code(), null, 99L);
    }

    @Test
    void updateWorklistShouldRejectCancelledWorklist() throws Exception {
        WorklistDetailRow Worklist = baseWorklist(1501L, WorklistStatus.CANCELLED);
        Worklist.setDicomServerWorklistId("wl-1501");
        when(WorklistMapper.findWorklistById(11L, 1501L)).thenReturn(Worklist);

        WorklistUpdateRequest request = new WorklistUpdateRequest();
        request.setModalityId(5L);
        request.setStudyDescription("Updated Study");
        request.setScheduledDate(LocalDate.of(2026, 5, 24));

        ResponseMessage<BaseResult> response = WorklistService.updateWorklist(1501L, request, null);

        assertFalse(response.isSuccess());
        verify(dicomServerClientService, never()).updateWorklistById(any(), any());
    }

    @Test
    void updateWorklistShouldUpdateDicomServerForInProgressWorklist() throws Exception {
        WorklistDetailRow Worklist = baseWorklist(15015L, WorklistStatus.IN_PROGRESS);
        Worklist.setDicomServerId(4L);
        Worklist.setDicomServerWorklistId("wl-15015");
        Worklist.setDicomServerWorklistPath("/worklists/wl-15015");
        Worklist.setStudyDescription("Old Study");
        Worklist.setScheduledDate(LocalDate.of(2026, 5, 23));
        Worklist.setScheduledTime(LocalTime.of(8, 30));

        WorklistDetailRow refreshed = baseWorklist(15015L, WorklistStatus.IN_PROGRESS);
        refreshed.setDicomServerId(4L);
        refreshed.setDicomServerWorklistId("wl-15015");
        refreshed.setDicomServerWorklistPath("/worklists/wl-15015");
        refreshed.setStudyDescription("Updated PACS Study");
        refreshed.setScheduledDate(LocalDate.of(2026, 5, 24));
        refreshed.setScheduledTime(LocalTime.of(10, 15));

        HospitalDicomServerResponse targetServer = new HospitalDicomServerResponse();
        targetServer.setId(4L);
        targetServer.setBaseUrl("http://localhost:8042");
        targetServer.setUsername("dicom_server");
        targetServer.setPassword("dicom_server");
        targetServer.setAeTitle("dicom_server");

        ModalityResponse modality = new ModalityResponse();
        modality.setAbbr("CT");

        DicomServerWorklistResponse remoteWorklist = worklistResponse(
                "wl-15015",
                "/worklists/wl-15015",
                "CT",
                "Updated PACS Study",
                "20260524",
                "101500"
        );

        when(WorklistMapper.findWorklistById(11L, 15015L)).thenReturn(Worklist, refreshed);
        when(modalityMapper.countActiveModalitiesByIds(List.of(5L))).thenReturn(1L);
        when(modalityMapper.getModalityById(5L)).thenReturn(List.of(modality));
        when(dicomServerMapper.findActiveDicomServerByWorklist(11L, 15015L)).thenReturn(targetServer);
        when(dicomServerClientService.updateWorklistById(
                eq("http://localhost:8042"),
                eq("dicom_server"),
                eq("dicom_server"),
                eq("wl-15015"),
                any(DicomServerWorklistCreateRequest.class)
        )).thenReturn(remoteWorklist);
        when(WorklistMapper.updateWorklistDicomWorklistFieldsById(
                eq(11L),
                eq(15015L),
                eq(5L),
                eq("ACC-15015"),
                eq("CT"),
                eq("DICOM_SERVER"),
                eq("Updated PACS Study"),
                eq(LocalDate.of(2026, 5, 24)),
                eq(LocalTime.of(10, 15)),
                eq("wl-15015"),
                eq("/worklists/wl-15015"),
                eq(99L)
        )).thenReturn(1);

        WorklistUpdateRequest request = new WorklistUpdateRequest();
        request.setModalityId(5L);
        request.setStudyDescription("Updated PACS Study");
        request.setScheduledDate(LocalDate.of(2026, 5, 24));
        request.setScheduledTime(LocalTime.of(10, 15));
        request.setNotes("update from ui");

        ResponseMessage<BaseResult> response = WorklistService.updateWorklist(15015L, request, null);

        assertTrue(response.isSuccess(), response.getHeader() != null ? String.valueOf(response.getHeader().getErrorText()) : "Unknown error");
        verify(dicomServerClientService).updateWorklistById(
                eq("http://localhost:8042"),
                eq("dicom_server"),
                eq("dicom_server"),
                eq("wl-15015"),
                argThat((DicomServerWorklistCreateRequest payload) ->
                        payload != null
                                && payload.getTags() != null
                                && "ACC-15015".equals(payload.getTags().getAccessionNumber())
                                && "Updated PACS Study".equals(payload.getTags().getStudyDescription())
                                && payload.getTags().getScheduledProcedureStepSequence() != null
                                && !payload.getTags().getScheduledProcedureStepSequence().isEmpty()
                                && "CT".equals(payload.getTags().getScheduledProcedureStepSequence().get(0).getModality())
                                && "20260524".equals(payload.getTags().getScheduledProcedureStepSequence().get(0).getScheduledProcedureStepStartDate())
                                && "101500".equals(payload.getTags().getScheduledProcedureStepSequence().get(0).getScheduledProcedureStepStartTime())
                )
        );
        verify(WorklistMapper).insertHistory(
                eq(11L),
                eq(15015L),
                eq(501L),
                eq(WorklistStatus.IN_PROGRESS.code()),
                eq(WorklistStatus.IN_PROGRESS.code()),
                eq(WorklistConstants.ACTION_UPDATE),
                eq("update from ui"),
                eq(99L)
        );
    }

    @Test
    void viewStudyShouldResolveStudyMetadataUsingWorklistDicomServer() throws Exception {
        WorklistDetailRow Worklist = baseWorklist(1660L, WorklistStatus.IN_PROGRESS);
        Worklist.setDicomServerId(4L);
        Worklist.setStudyUuid("1.2.840.113619.102201.1660");
        Worklist.setStudyInstanceUid("1.2.840.113619.102201.1660");

        HospitalDicomServerResponse targetServer = new HospitalDicomServerResponse();
        targetServer.setId(4L);
        targetServer.setBaseUrl("http://localhost:8042");
        targetServer.setDicomServerUiBaseUrl("http://localhost:8042");
        targetServer.setDicomwebBaseUrl("http://localhost:8042/dicom-web");
        targetServer.setViewerBaseUrl("http://localhost:3005");
        targetServer.setPacsApiCallbackBaseUrl("http://localhost:8080/pacsApi");
        targetServer.setUsername("dicom_server");
        targetServer.setPassword("dicom_server");
        targetServer.setAeTitle("dicom_server");

        DicomServerStudyResponse studyResponse = new DicomServerStudyResponse();
        studyResponse.setId("dicom_server_study-1660");
        studyResponse.setMainDicomTags(Map.of("StudyInstanceUID", "1.2.840.113619.102201.1660"));
        studyResponse.setInstances(List.of("instance-1", "instance-2"));

        when(WorklistMapper.findWorklistById(11L, 1660L)).thenReturn(Worklist);
        when(dicomServerMapper.findActiveDicomServerByWorklist(11L, 1660L)).thenReturn(targetServer);
        when(dicomServerClientService.findStudyIdsByAccessionNumber(
                eq("http://localhost:8042"),
                eq("dicom_server"),
                eq("dicom_server"),
                any()
        )).thenReturn(List.of("dicom_server_study-1660"));
        when(dicomServerClientService.getStudyById(
                eq("http://localhost:8042"),
                eq("dicom_server"),
                eq("dicom_server"),
                eq("dicom_server_study-1660")
        )).thenReturn(studyResponse);
        when(jwtTokenService.issueViewerDicomwebToken(
                eq(11L),
                eq(1660L),
                eq(null),
                eq("1.2.840.113619.102201.1660"),
                anyLong()
        )).thenReturn(new AccessTokenResponse("Bearer", "viewer-token", null, 1800, "pacs.viewer.dicomweb"));

        WorklistViewStudyRequest request = new WorklistViewStudyRequest();
        request.setWorklistId(1660L);

        ResponseMessage<BaseResult> response = WorklistService.viewStudy(request, null);

        assertTrue(response.isSuccess());
        WorklistViewerStudyResponse viewerStudy =
                (WorklistViewerStudyResponse) response.getBody().getData().get(0);
        assertEquals("http://localhost:3005", viewerStudy.getViewerBaseUrl());
        assertEquals("http://localhost:3005/pacs-dicomweb", viewerStudy.getDicomwebBaseUrl());
        assertEquals("http://localhost:8080/pacsApi/worklist/viewer-dicom-web/viewer-token/11/1660", viewerStudy.getDicomwebGatewayBaseUrl());
        assertEquals("viewer-token", viewerStudy.getDicomwebAuthToken());
        assertEquals("http://localhost:8042", viewerStudy.getDicomServerUiBaseUrl());
        assertEquals("http://localhost:8042/app/explorer.html#study?uuid=dicom_server_study-1660", viewerStudy.getViewerUrl());
        assertEquals(ViewerAccessKeyService.ACCESS_EDIT, viewerStudy.getViewerAccess());
        assertEquals(Boolean.TRUE, viewerStudy.getCanEditResult());
        assertEquals(Boolean.TRUE, viewerStudy.getCanEditViewerState());
        verify(dicomServerClientService, never()).findStudyIdsByAccessionNumber(any());
        verify(dicomServerClientService).findStudyIdsByAccessionNumber(
                eq("http://localhost:8042"),
                eq("dicom_server"),
                eq("dicom_server"),
                any()
        );
        verify(dicomServerClientService).getStudyById(
                eq("http://localhost:8042"),
                eq("dicom_server"),
                eq("dicom_server"),
                eq("dicom_server_study-1660")
        );
    }

    @Test
    void deleteWorklistShouldCancelWorklistWhenDicomServerWorklistAlreadyMissing() throws Exception {
        WorklistDetailRow Worklist = baseWorklist(1601L, WorklistStatus.IN_PROGRESS);
        Worklist.setDicomServerId(4L);
        Worklist.setDicomServerWorklistId("wl-1601");

        WorklistDetailRow refreshed = baseWorklist(1601L, WorklistStatus.CANCELLED);
        refreshed.setDicomServerId(4L);
        refreshed.setDicomServerWorklistId("wl-1601");

        HospitalDicomServerResponse targetServer = new HospitalDicomServerResponse();
        targetServer.setId(4L);
        targetServer.setBaseUrl("http://localhost:8042");
        targetServer.setUsername("dicom_server");
        targetServer.setPassword("dicom_server");

        when(WorklistMapper.findWorklistById(11L, 1601L)).thenReturn(Worklist, Worklist, refreshed);
        when(dicomServerMapper.findActiveDicomServerByWorklist(11L, 1601L)).thenReturn(targetServer);
        when(WorklistMapper.updateWorklistStatusById(11L, 1601L, WorklistStatus.CANCELLED.code(), 99L)).thenReturn(1);
        doThrow(notFound()).when(dicomServerClientService).deleteWorklistById(
                "http://localhost:8042",
                "dicom_server",
                "dicom_server",
                "wl-1601"
        );

        ResponseMessage<BaseResult> response = WorklistService.deleteWorklist(1601L, null);

        assertTrue(response.isSuccess());
        verify(WorklistMapper).updateWorklistStatusById(11L, 1601L, WorklistStatus.CANCELLED.code(), 99L);
        verify(WorklistMapper).insertHistory(
                eq(11L),
                eq(1601L),
                eq(501L),
                eq(WorklistStatus.IN_PROGRESS.code()),
                eq(WorklistStatus.CANCELLED.code()),
                eq(WorklistConstants.ACTION_WORKLIST_DELETE),
                eq("DicomServer worklist already missing: wl-1601"),
                eq(99L)
        );
    }

    @Test
    void cancelShouldCancelWaitingWorklistWithoutDeletingWorklist() throws Exception {
        WorklistDetailRow Worklist = baseWorklist(1701L, WorklistStatus.WAITING);
        Worklist.setDicomServerWorklistId("wl-1701");

        when(WorklistMapper.findWorklistById(11L, 1701L)).thenReturn(Worklist);
        when(WorklistMapper.updateWorklistWorkflowStatusById(11L, 1701L, WorklistStatus.CANCELLED.code(), null, 99L)).thenReturn(1);

        WorklistActionRequest request = new WorklistActionRequest();
        request.setId(1701L);
        request.setNotes("cancel from ui");

        ResponseMessage<BaseResult> response = WorklistService.updateStatus(request, WorklistStatus.CANCELLED.name(), null);

        assertTrue(response.isSuccess());
        verify(dicomServerClientService, never()).deleteWorklistById("wl-1701");
        verify(WorklistMapper).insertHistory(
                eq(11L),
                eq(1701L),
                eq(501L),
                eq(WorklistStatus.WAITING.code()),
                eq(WorklistStatus.CANCELLED.code()),
                eq(WorklistConstants.ACTION_CANCEL),
                eq("cancel from ui"),
                eq(99L)
        );
    }

    @Test
    void cancelShouldDeleteDicomServerWorklistForInProgressWorklist() throws Exception {
        WorklistDetailRow Worklist = baseWorklist(1702L, WorklistStatus.IN_PROGRESS);
        Worklist.setDicomServerId(4L);
        Worklist.setDicomServerWorklistId("wl-1702");

        WorklistDetailRow refreshed = baseWorklist(1702L, WorklistStatus.CANCELLED);
        refreshed.setDicomServerId(4L);
        refreshed.setDicomServerWorklistId("wl-1702");

        HospitalDicomServerResponse targetServer = new HospitalDicomServerResponse();
        targetServer.setId(4L);
        targetServer.setBaseUrl("http://localhost:8042");
        targetServer.setUsername("dicom_server");
        targetServer.setPassword("dicom_server");

        when(WorklistMapper.findWorklistById(11L, 1702L)).thenReturn(Worklist, refreshed);
        when(dicomServerMapper.findActiveDicomServerByWorklist(11L, 1702L)).thenReturn(targetServer);
        when(WorklistMapper.updateWorklistWorkflowStatusById(11L, 1702L, WorklistStatus.CANCELLED.code(), null, 99L)).thenReturn(1);

        WorklistActionRequest request = new WorklistActionRequest();
        request.setId(1702L);
        request.setNotes("cancel from ui");

        ResponseMessage<BaseResult> response = WorklistService.updateStatus(request, WorklistStatus.CANCELLED.name(), null);

        assertTrue(response.isSuccess());
        verify(dicomServerClientService).deleteWorklistById(
                "http://localhost:8042",
                "dicom_server",
                "dicom_server",
                "wl-1702"
        );
        verify(WorklistMapper).updateWorklistWorkflowStatusById(11L, 1702L, WorklistStatus.CANCELLED.code(), null, 99L);
        verify(WorklistMapper).insertHistory(
                eq(11L),
                eq(1702L),
                eq(501L),
                eq(WorklistStatus.IN_PROGRESS.code()),
                eq(WorklistStatus.CANCELLED.code()),
                eq(WorklistConstants.ACTION_CANCEL),
                eq("cancel from ui"),
                eq(99L)
        );
    }

    @Test
    void cancelShouldRejectWhenWorklistAlreadyHasLocalImagingEvidence() throws Exception {
        WorklistDetailRow Worklist = baseWorklist(1703L, WorklistStatus.IN_PROGRESS);
        Worklist.setDicomServerId(4L);
        Worklist.setDicomServerWorklistId("wl-1703");
        Worklist.setDicomServerStudyId("study-1703");
        when(WorklistMapper.findWorklistById(11L, 1703L)).thenReturn(Worklist);

        WorklistActionRequest request = new WorklistActionRequest();
        request.setId(1703L);

        ResponseMessage<BaseResult> response = WorklistService.updateStatus(request, WorklistStatus.CANCELLED.name(), null);

        assertFalse(response.isSuccess());
        assertEquals(WorklistConstants.MSG_IMAGING_STARTED_CANNOT_CANCEL, response.getHeader().getErrorText());
        verify(dicomServerClientService, never()).deleteWorklistById(anyString(), any(), any(), anyString());
        verify(WorklistMapper, never()).updateWorklistWorkflowStatusById(anyLong(), anyLong(), any(), any(), anyLong());
    }

    @Test
    void cancelShouldRejectWhenDicomServerHasStudyForAccession() throws Exception {
        WorklistDetailRow Worklist = baseWorklist(1704L, WorklistStatus.IN_PROGRESS);
        Worklist.setDicomServerId(4L);
        Worklist.setDicomServerWorklistId("wl-1704");

        HospitalDicomServerResponse targetServer = new HospitalDicomServerResponse();
        targetServer.setId(4L);
        targetServer.setBaseUrl("http://localhost:8042");
        targetServer.setUsername("dicom_server");
        targetServer.setPassword("dicom_server");

        when(WorklistMapper.findWorklistById(11L, 1704L)).thenReturn(Worklist);
        when(dicomServerMapper.findActiveDicomServerByWorklist(11L, 1704L)).thenReturn(targetServer);
        when(dicomServerClientService.findStudyIdsByAccessionNumber(
                eq("http://localhost:8042"),
                eq("dicom_server"),
                eq("dicom_server"),
                any()
        )).thenReturn(List.of("study-1704"));

        WorklistActionRequest request = new WorklistActionRequest();
        request.setId(1704L);

        ResponseMessage<BaseResult> response = WorklistService.updateStatus(request, WorklistStatus.CANCELLED.name(), null);

        assertFalse(response.isSuccess());
        assertEquals(WorklistConstants.MSG_IMAGING_STARTED_CANNOT_CANCEL, response.getHeader().getErrorText());
        verify(dicomServerClientService, never()).deleteWorklistById(anyString(), any(), any(), anyString());
        verify(WorklistMapper, never()).updateWorklistWorkflowStatusById(anyLong(), anyLong(), any(), any(), anyLong());
    }

    @Test
    void deleteWorklistShouldRejectWhenDicomServerHasStudyForAccession() throws Exception {
        WorklistDetailRow Worklist = baseWorklist(1705L, WorklistStatus.IN_PROGRESS);
        Worklist.setDicomServerId(4L);
        Worklist.setDicomServerWorklistId("wl-1705");

        HospitalDicomServerResponse targetServer = new HospitalDicomServerResponse();
        targetServer.setId(4L);
        targetServer.setBaseUrl("http://localhost:8042");
        targetServer.setUsername("dicom_server");
        targetServer.setPassword("dicom_server");

        when(WorklistMapper.findWorklistById(11L, 1705L)).thenReturn(Worklist);
        when(dicomServerMapper.findActiveDicomServerByWorklist(11L, 1705L)).thenReturn(targetServer);
        when(dicomServerClientService.findStudyIdsByAccessionNumber(
                eq("http://localhost:8042"),
                eq("dicom_server"),
                eq("dicom_server"),
                any()
        )).thenReturn(List.of("study-1705"));

        ResponseMessage<BaseResult> response = WorklistService.deleteWorklist(1705L, null);

        assertFalse(response.isSuccess());
        assertEquals(WorklistConstants.MSG_IMAGING_STARTED_CANNOT_CANCEL, response.getHeader().getErrorText());
        verify(dicomServerClientService, never()).deleteWorklistById(anyString(), any(), any(), anyString());
        verify(WorklistMapper, never()).updateWorklistStatusById(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void cancelShouldDeleteRemoteWorklistForFailedWorklistThatWasAlreadySent() throws Exception {
        WorklistDetailRow Worklist = baseWorklist(1706L, WorklistStatus.FAILED);
        Worklist.setDicomServerId(4L);
        Worklist.setDicomServerWorklistId("wl-1706");

        WorklistDetailRow refreshed = baseWorklist(1706L, WorklistStatus.CANCELLED);
        refreshed.setDicomServerId(4L);
        refreshed.setDicomServerWorklistId("wl-1706");

        HospitalDicomServerResponse targetServer = new HospitalDicomServerResponse();
        targetServer.setId(4L);
        targetServer.setBaseUrl("http://localhost:8042");
        targetServer.setUsername("dicom_server");
        targetServer.setPassword("dicom_server");

        when(WorklistMapper.findWorklistById(11L, 1706L)).thenReturn(Worklist, refreshed);
        when(dicomServerMapper.findActiveDicomServerByWorklist(11L, 1706L)).thenReturn(targetServer);
        when(WorklistMapper.updateWorklistWorkflowStatusById(11L, 1706L, WorklistStatus.CANCELLED.code(), null, 99L)).thenReturn(1);

        WorklistActionRequest request = new WorklistActionRequest();
        request.setId(1706L);
        request.setNotes("cancel failed sent worklist");

        ResponseMessage<BaseResult> response = WorklistService.updateStatus(request, WorklistStatus.CANCELLED.name(), null);

        assertTrue(response.isSuccess());
        verify(dicomServerClientService).deleteWorklistById(
                "http://localhost:8042",
                "dicom_server",
                "dicom_server",
                "wl-1706"
        );
        verify(WorklistMapper).insertHistory(
                eq(11L),
                eq(1706L),
                eq(501L),
                eq(WorklistStatus.FAILED.code()),
                eq(WorklistStatus.CANCELLED.code()),
                eq(WorklistConstants.ACTION_CANCEL),
                eq("cancel failed sent worklist"),
                eq(99L)
        );
    }

    private static WorklistDetailRow baseWorklist(Long id, WorklistStatus status) {
        WorklistDetailRow Worklist = new WorklistDetailRow();
        Worklist.setId(id);
        Worklist.setHospitalId(11L);
        Worklist.setPatientId(501L);
        Worklist.setPatientUid("26P0000001");
        Worklist.setPatientName("Soklin Test");
        Worklist.setDob(LocalDate.of(2026, 5, 21));
        Worklist.setSex("M");
        Worklist.setModalityId(5L);
        Worklist.setModalityName("CT");
        Worklist.setVisitCode("VISIT-" + id);
        Worklist.setAccessionNumber("ACC-" + id);
        Worklist.setModalityCode("CT");
        Worklist.setMachineAeTitle("dicom_server");
        Worklist.setStatus(status.name());
        return Worklist;
    }

    private static DicomServerWorklistResponse worklistResponse(
            String id,
            String path,
            String modality,
            String studyDescription,
            String scheduledDate,
            String scheduledTime
    ) {
        DicomServerWorklistResponse.ScheduledProcedureStep step = new DicomServerWorklistResponse.ScheduledProcedureStep();
        step.setModality(modality);
        step.setScheduledStationAETitle("dicom_server");
        step.setScheduledProcedureStepStartDate(scheduledDate);
        step.setScheduledProcedureStepStartTime(scheduledTime);
        step.setScheduledProcedureStepDescription(studyDescription);
        step.setScheduledProcedureStepID("ACC-" + id.replace("wl-", ""));

        DicomServerWorklistResponse.Tags tags = new DicomServerWorklistResponse.Tags();
        tags.setPatientID("26P0000001");
        tags.setPatientName("Soklin Test");
        tags.setPatientBirthDate("20260521");
        tags.setPatientSex("M");
        tags.setAccessionNumber("ACC-" + id.replace("wl-", ""));
        tags.setStudyDescription(studyDescription);
        tags.setRequestedProcedureID("ACC-" + id.replace("wl-", ""));
        tags.setRequestedProcedureDescription(studyDescription);
        tags.setScheduledProcedureStepSequence(List.of(step));

        DicomServerWorklistResponse response = new DicomServerWorklistResponse();
        response.setId(id);
        response.setPath(path);
        response.setTags(tags);
        return response;
    }

    private static HttpClientErrorException notFound() {
        return HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
    }
}
