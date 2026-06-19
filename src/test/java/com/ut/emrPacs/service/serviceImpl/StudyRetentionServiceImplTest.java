package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.mapper.pacs.StudyRetentionMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionAutoDeleteRequest;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionBulkDeleteRequest;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionPolicySaveRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionBulkDeleteResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionPolicyResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionReviewResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DicomServerClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyRetentionServiceImplTest {

    @Mock
    private StudyRetentionMapper studyRetentionMapper;
    @Mock
    private DicomServerMapper dicomServerMapper;
    @Mock
    private DicomServerClientService dicomServerClientService;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private PublicEntityKeyResolver publicEntityKeyResolver;
    @Mock
    private PlatformTransactionManager transactionManager;

    private StudyRetentionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudyRetentionServiceImpl();
        ReflectionTestUtils.setField(service, "studyRetentionMapper", studyRetentionMapper);
        ReflectionTestUtils.setField(service, "dicomServerMapper", dicomServerMapper);
        ReflectionTestUtils.setField(service, "dicomServerClientService", dicomServerClientService);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());
        ReflectionTestUtils.setField(service, "activityLogService", activityLogService);
        ReflectionTestUtils.setField(service, "publicEntityKeyResolver", publicEntityKeyResolver);
        ReflectionTestUtils.setField(service, "transactionManager", transactionManager);
        ReflectionTestUtils.setField(service, "autoDeleteSchedulerEnabled", false);
        ReflectionTestUtils.setField(service, "scheduledAutoDeleteChunkSize", 25);
        ReflectionTestUtils.setField(service, "scheduledAutoDeleteMaxItems", 100);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "super.admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
        authentication.setDetails(new CurrentUserPrincipal(1L, "super.admin", null, null, "pacs-web", "jti", 1L));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(dicomServerMapper.getDicomServerById(anyLong(), anyLong())).thenReturn(List.of(server()));
        lenient().when(studyRetentionMapper.hardDeleteStudyData(anyLong(), anyLong())).thenReturn(1);
        lenient().when(publicEntityKeyResolver.resolve(any(PublicEntityKeyResolver.Entity.class), nullable(String.class), nullable(Long.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findPolicyUsesCurrentHospitalScopeForAdminUsers() throws Exception {
        setAuthenticatedUser(2L, 10L, "ROLE_ADMIN");
        StudyRetentionPolicyResponse policy = policy(55L, "policy-55", 10L);

        when(studyRetentionMapper.findPolicyById(55L, 10L)).thenReturn(policy);

        ResponseMessage<BaseResult> response = service.findPolicy(55L, new MockHttpServletRequest());

        assertTrue(response.getHeader().getResult());
        assertNotNull(response.getBody());
        assertEquals(policy, response.getBody().getData().get(0));
        verify(studyRetentionMapper).findPolicyById(55L, 10L);
    }

    @Test
    void savePolicyUpdateUsesPublicKeyAndCurrentHospitalScope() throws Exception {
        setAuthenticatedUser(2L, 10L, "ROLE_ADMIN");
        StudyRetentionPolicySaveRequest request = new StudyRetentionPolicySaveRequest();
        request.setPublicKey("policy-55");
        request.setRetentionValue(2);
        request.setRetentionUnit("years");
        request.setNotifyBeforeDays(14);
        request.setRequireApproval(true);
        request.setEnabled(true);
        request.setAutoDelete(false);
        request.setNotes("Updated retention");

        StudyRetentionPolicyResponse existing = policy(55L, "policy-55", 10L);
        StudyRetentionPolicyResponse saved = policy(55L, "policy-55", 10L);
        saved.setRetentionValue(2);
        saved.setRetentionUnit("YEAR");
        saved.setRetentionDays(730);
        saved.setNotes("Updated retention");

        when(publicEntityKeyResolver.resolve(PublicEntityKeyResolver.Entity.STUDY_RETENTION_POLICY, "policy-55", null)).thenReturn(55L);
        when(studyRetentionMapper.countDuplicatePolicyScope(eq(10L), nullable(Long.class), nullable(Long.class), eq(55L))).thenReturn(0L);
        when(studyRetentionMapper.findPolicyById(55L, 10L)).thenReturn(existing);
        when(studyRetentionMapper.findPolicyById(55L, null)).thenReturn(saved);

        ResponseMessage<BaseResult> response = service.savePolicy(request, new MockHttpServletRequest());

        assertTrue(response.getHeader().getResult(), () -> String.valueOf(response.getHeader().getErrorText()));
        assertEquals(55L, request.getId());
        assertEquals(10L, request.getHospitalId());
        assertEquals("YEAR", request.getRetentionUnit());
        assertEquals(730, request.getRetentionDays());
        verify(studyRetentionMapper).updatePolicy(request, 2L);
        verify(studyRetentionMapper, never()).insertPolicy(any(), anyLong());
    }

    @Test
    void savePolicyUpdateRejectsPolicyOutsideCurrentHospital() throws Exception {
        setAuthenticatedUser(2L, 10L, "ROLE_ADMIN");
        StudyRetentionPolicySaveRequest request = new StudyRetentionPolicySaveRequest();
        request.setPublicKey("policy-other");
        request.setRetentionValue(1);
        request.setRetentionUnit("YEAR");
        request.setNotifyBeforeDays(14);

        when(publicEntityKeyResolver.resolve(PublicEntityKeyResolver.Entity.STUDY_RETENTION_POLICY, "policy-other", null)).thenReturn(77L);
        lenient().when(studyRetentionMapper.countDuplicatePolicyScope(eq(10L), nullable(Long.class), nullable(Long.class), eq(77L))).thenReturn(0L);
        when(studyRetentionMapper.findPolicyById(77L, 10L)).thenReturn(null);

        ResponseMessage<BaseResult> response = service.savePolicy(request, new MockHttpServletRequest());

        assertEquals(false, response.getHeader().getResult());
        verify(studyRetentionMapper, never()).updatePolicy(any(), anyLong());
        verify(studyRetentionMapper, never()).insertPolicy(any(), anyLong());
    }

    @Test
    void savePolicyRejectsRetentionLongerThanTenYears() throws Exception {
        StudyRetentionPolicySaveRequest request = new StudyRetentionPolicySaveRequest();
        request.setRetentionValue(11);
        request.setRetentionUnit("YEAR");
        request.setNotifyBeforeDays(14);

        ResponseMessage<BaseResult> response = service.savePolicy(request, new MockHttpServletRequest());

        assertEquals(false, response.getHeader().getResult());
        assertEquals(
                "Retention period cannot exceed 3650 days (117 months or 10 years).",
                response.getHeader().getErrorText()
        );
        verify(studyRetentionMapper, never()).insertPolicy(any(), anyLong());
        verify(studyRetentionMapper, never()).updatePolicy(any(), anyLong());
    }

    @Test
    void savePolicyAcceptsTenYearRetention() throws Exception {
        StudyRetentionPolicySaveRequest request = new StudyRetentionPolicySaveRequest();
        request.setRetentionValue(10);
        request.setRetentionUnit("YEAR");
        request.setNotifyBeforeDays(365);

        StudyRetentionPolicyResponse saved = policy(88L, "policy-88", null);
        saved.setRetentionValue(10);
        saved.setRetentionDays(3650);

        when(studyRetentionMapper.countDuplicatePolicyScope(null, null, null, null)).thenReturn(0L);
        when(studyRetentionMapper.insertPolicy(request, 1L)).thenAnswer(invocation -> {
            request.setId(88L);
            return 1;
        });
        when(studyRetentionMapper.findPolicyById(88L, null)).thenReturn(saved);

        ResponseMessage<BaseResult> response = service.savePolicy(request, new MockHttpServletRequest());

        assertTrue(response.getHeader().getResult(), () -> String.valueOf(response.getHeader().getErrorText()));
        assertEquals(3650, request.getRetentionDays());
        verify(studyRetentionMapper).insertPolicy(request, 1L);
    }

    @Test
    void bulkDeleteDeletesSelectedStudiesInChunksAndReportsMissingRows() throws Exception {
        StudyRetentionBulkDeleteRequest request = new StudyRetentionBulkDeleteRequest();
        request.setStudyPublicKeys(List.of("Study-A", "study-b", "study-c", "missing", "study-a"));
        request.setChunkSize(2);

        when(studyRetentionMapper.listReviewCandidatesByStudyPublicKeys(isNull(), eq(List.of("study-a", "study-b"))))
                .thenReturn(List.of(candidate("study-a", "EXPIRED_WAITING_APPROVAL", 101L), candidate("study-b", "DELETE_FAILED", 102L)));
        when(studyRetentionMapper.listReviewCandidatesByStudyPublicKeys(isNull(), eq(List.of("study-c", "missing"))))
                .thenReturn(List.of(candidate("study-c", "PENDING_APPROVAL", 103L)));
        when(studyRetentionMapper.createDeleteRequest(any(StudyRetentionReviewResponse.class), eq(1L)))
                .thenReturn(1001L, 1002L, 1003L);

        StudyRetentionBulkDeleteResponse body = extractBulkResponse(service.bulkDelete(request, new MockHttpServletRequest()));

        assertEquals("BULK_DELETE", body.getMode());
        assertEquals(4, body.getRequested());
        assertEquals(4, body.getProcessed());
        assertEquals(3, body.getDeleted());
        assertEquals(0, body.getFailed());
        assertEquals(1, body.getSkipped());
        assertEquals(2, body.getChunkSize());
        assertEquals(2, body.getChunks());
        verify(studyRetentionMapper).listReviewCandidatesByStudyPublicKeys(isNull(), eq(List.of("study-a", "study-b")));
        verify(studyRetentionMapper).listReviewCandidatesByStudyPublicKeys(isNull(), eq(List.of("study-c", "missing")));
    }

    @Test
    void bulkDeleteSkipsRowsThatAreNotReadyForDeletion() throws Exception {
        StudyRetentionBulkDeleteRequest request = new StudyRetentionBulkDeleteRequest();
        request.setStudyPublicKeys(List.of("study-active"));

        when(studyRetentionMapper.listReviewCandidatesByStudyPublicKeys(isNull(), eq(List.of("study-active"))))
                .thenReturn(List.of(candidate("study-active", "NEAR_EXPIRY", 201L)));

        StudyRetentionBulkDeleteResponse body = extractBulkResponse(service.bulkDelete(request, new MockHttpServletRequest()));

        assertEquals(1, body.getRequested());
        assertEquals(1, body.getProcessed());
        assertEquals(0, body.getDeleted());
        assertEquals(0, body.getFailed());
        assertEquals(1, body.getSkipped());
        verify(studyRetentionMapper, never()).createDeleteRequest(any(StudyRetentionReviewResponse.class), anyLong());
        verify(dicomServerClientService, never()).deleteStudyById(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void runAutoDeleteDeletesAutoReadyCandidatesInChunks() throws Exception {
        StudyRetentionAutoDeleteRequest request = new StudyRetentionAutoDeleteRequest();
        request.setMaxItems(3);
        request.setChunkSize(2);

        when(studyRetentionMapper.listAutoDeleteCandidates(isNull(), eq(3)))
                .thenReturn(List.of(
                        candidate("auto-a", "AUTO_DELETE_READY", 301L),
                        candidate("auto-b", "AUTO_DELETE_READY", 302L),
                        candidate("auto-c", "AUTO_DELETE_READY", 303L)
                ));
        when(studyRetentionMapper.createDeleteRequest(any(StudyRetentionReviewResponse.class), eq(1L)))
                .thenReturn(2001L, 2002L, 2003L);

        StudyRetentionBulkDeleteResponse body = extractBulkResponse(service.runAutoDelete(request, new MockHttpServletRequest()));

        assertEquals("MANUAL_AUTO_RUN", body.getMode());
        assertEquals(3, body.getRequested());
        assertEquals(3, body.getProcessed());
        assertEquals(3, body.getDeleted());
        assertEquals(0, body.getFailed());
        assertEquals(0, body.getSkipped());
        assertEquals(2, body.getChunkSize());
        assertEquals(2, body.getChunks());
        verify(studyRetentionMapper).listAutoDeleteCandidates(isNull(), eq(3));
    }

    @Test
    void runAutoDeleteRecordsFailureAndContinuesWithNextCandidate() throws Exception {
        StudyRetentionAutoDeleteRequest request = new StudyRetentionAutoDeleteRequest();
        request.setMaxItems(2);
        request.setChunkSize(2);

        when(studyRetentionMapper.listAutoDeleteCandidates(isNull(), eq(2)))
                .thenReturn(List.of(candidate("auto-failed", "AUTO_DELETE_READY", 401L), candidate("auto-ok", "AUTO_DELETE_READY", 402L)));
        when(studyRetentionMapper.createDeleteRequest(any(StudyRetentionReviewResponse.class), eq(1L)))
                .thenReturn(3001L, 3002L);
        doThrow(new RuntimeException("delete failed"))
                .doNothing()
                .when(dicomServerClientService)
                .deleteStudyById(anyString(), anyString(), anyString(), anyString());

        StudyRetentionBulkDeleteResponse body = extractBulkResponse(service.runAutoDelete(request, new MockHttpServletRequest()));

        assertEquals(2, body.getProcessed());
        assertEquals(1, body.getDeleted());
        assertEquals(1, body.getFailed());
        assertEquals(0, body.getSkipped());
        verify(studyRetentionMapper).markRequestDeleteFailed(eq(3001L), anyString());
        verify(studyRetentionMapper).markRequestDeleted(3002L, 1L);
    }

    private static StudyRetentionBulkDeleteResponse extractBulkResponse(ResponseMessage<BaseResult> response) {
        assertTrue(response.getHeader().getResult());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        assertEquals(1, response.getBody().getData().size());
        return (StudyRetentionBulkDeleteResponse) response.getBody().getData().get(0);
    }

    private static StudyRetentionPolicyResponse policy(Long id, String publicKey, Long hospitalId) {
        StudyRetentionPolicyResponse response = new StudyRetentionPolicyResponse();
        response.setId(id);
        response.setPublicKey(publicKey);
        response.setHospitalId(hospitalId);
        response.setHospitalName("KSFH Hospital");
        response.setRetentionValue(1);
        response.setRetentionUnit("YEAR");
        response.setRetentionDays(365);
        response.setNotifyBeforeDays(14);
        response.setRequireApproval(true);
        response.setEnabled(true);
        response.setAutoDelete(false);
        return response;
    }

    private static void setAuthenticatedUser(Long userId, Long hospitalId, String... authorities) {
        List<SimpleGrantedAuthority> grantedAuthorities = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "user-" + userId,
                "n/a",
                grantedAuthorities
        );
        authentication.setDetails(new CurrentUserPrincipal(userId, "user-" + userId, hospitalId, "HSP", "pacs-web", "jti", 1L));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static StudyRetentionReviewResponse candidate(String publicKey, String status, Long studyId) {
        StudyRetentionReviewResponse response = new StudyRetentionReviewResponse();
        response.setStudyId(studyId);
        response.setStudyPublicKey(publicKey);
        response.setHospitalId(10L);
        response.setHospitalName("KSFH Hospital");
        response.setDicomServerId(20L);
        response.setDicomServerName("UDAYA_DICOM_SERVER_KSFH");
        response.setDicomServerStudyId("dicom-" + publicKey);
        response.setPatientName("Retention Test " + publicKey);
        response.setPatientMrn("MRN-" + publicKey);
        response.setAccessionNumber("ACC-" + publicKey);
        response.setStatus(status);
        response.setAutoDelete("AUTO_DELETE_READY".equals(status));
        return response;
    }

    private static HospitalDicomServerResponse server() {
        HospitalDicomServerResponse response = new HospitalDicomServerResponse();
        response.setBaseUrl("http://dicom-server.local");
        response.setUsername("dicom-user");
        response.setPassword("dicom-pass");
        return response;
    }
}
