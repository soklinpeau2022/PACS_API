package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.config.WorklistConstants;
import com.ut.emrPacs.mapper.modality.ModalityMapper;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.mapper.pacs.DicomServerCallbackLogMapper;
import com.ut.emrPacs.mapper.pacs.PatientMapper;
import com.ut.emrPacs.mapper.pacs.WorklistMapper;
import com.ut.emrPacs.mapper.pacs.StudyMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistReceivedStudyRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerStudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import com.ut.emrPacs.model.enums.WorklistStatus;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DicomServerClientService;
import com.ut.emrPacs.service.service.RealtimeNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorklistServiceImplReceivedStudyTest {

    @Mock
    private WorklistMapper WorklistMapper;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private ModalityMapper modalityMapper;
    @Mock
    private StudyMapper studyMapper;
    @Mock
    private DicomServerMapper dicomServerMapper;
    @Mock
    private DicomServerCallbackLogMapper dicomServerCallbackLogMapper;
    @Mock
    private DicomServerClientService dicomServerClientService;
    @Mock
    private RealtimeNotificationService realtimeNotificationService;

    private WorklistServiceImpl buildService() {
        WorklistServiceImpl WorklistService = new WorklistServiceImpl();
        ReflectionTestUtils.setField(WorklistService, "WorklistMapper", WorklistMapper);
        ReflectionTestUtils.setField(WorklistService, "messageService", new MessageService());
        ReflectionTestUtils.setField(WorklistService, "activityLogService", activityLogService);
        ReflectionTestUtils.setField(WorklistService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(WorklistService, "modalityMapper", modalityMapper);
        ReflectionTestUtils.setField(WorklistService, "studyMapper", studyMapper);
        ReflectionTestUtils.setField(WorklistService, "dicomServerMapper", dicomServerMapper);
        ReflectionTestUtils.setField(WorklistService, "dicomServerCallbackLogMapper", dicomServerCallbackLogMapper);
        ReflectionTestUtils.setField(WorklistService, "dicomServerClientService", dicomServerClientService);
        ReflectionTestUtils.setField(WorklistService, "realtimeNotificationService", realtimeNotificationService);
        return WorklistService;
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void receivedStudyShouldRejectWhenMachineTokenIsMissing() throws Exception {
        WorklistServiceImpl WorklistService = buildService();

        WorklistReceivedStudyRequest request = new WorklistReceivedStudyRequest();
        request.setAccessionNumber("VIEW-SM-0005");

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();

        ResponseMessage<BaseResult> response = WorklistService.receivedStudy(request, httpRequest);

        assertFalse(response.isSuccess());
        assertEquals(401, response.getHeader().getStatusCode());
        verify(WorklistMapper, never()).findWorklistByAccessionNumber(anyString());
        verify(dicomServerCallbackLogMapper).insertCallbackLog(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), eq(false), anyString(), any(), anyString());
        verifyNoSystemErrorActivity();
    }

    @Test
    void receivedStudyShouldUpdateWorklistByAccessionNumber() throws Exception {
        WorklistServiceImpl WorklistService = buildService();
        authenticateMachineClient();

        WorklistReceivedStudyRequest request = new WorklistReceivedStudyRequest();
        request.setEvent("STUDY_RECEIVED");
        request.setStatus("IN_PROGRESS");
        request.setAccessionNumber("VIEW-SM-0005");
        request.setDicomServerStudyId("dicom_server_study-1");
        request.setDicomServerPatientId("dicom_server_patient-1");
        request.setDicomServerSeriesIds(List.of("series-1"));
        request.setStudyInstanceUid("1.2.826.0.1.3680043.8.498.1");
        request.setStudyDescription("Viewer Microscopy");
        request.setStudyDate("20260524");

        WorklistDetailRow Worklist = new WorklistDetailRow();
        Worklist.setId(10L);
        Worklist.setHospitalId(99L);
        Worklist.setPatientId(100L);
        Worklist.setAccessionNumber("VIEW-SM-0005");
        Worklist.setStatus(WorklistStatus.IN_PROGRESS.name());
        Worklist.setModalityId(9L);
        Worklist.setModalityCode("SM");
        Worklist.setDicomServerId(4L);
        Worklist.setStudyDescription("Viewer Microscopy");
        when(WorklistMapper.findWorklistByAccessionNumber("VIEW-SM-0005")).thenReturn(Worklist);
        HospitalDicomServerResponse server = dicomServer(4L);
        when(dicomServerMapper.findActiveDicomServerByWorklist(99L, 10L)).thenReturn(server);
        DicomServerStudyResponse studyResponse = dicomServerStudy("dicom_server_study-1", "1.2.826.0.1.3680043.8.498.1", "VIEW-SM-0005", List.of("instance-1"));
        when(dicomServerClientService.getStudyById(
                eq("http://localhost:8042"),
                eq("dicom_server"),
                eq("secret"),
                eq("dicom_server_study-1")
        )).thenReturn(studyResponse);
        when(studyMapper.upsertFromWorklist(
                eq(99L),
                eq(100L),
                eq("1.2.826.0.1.3680043.8.498.1"),
                eq("VIEW-SM-0005"),
                eq(9L),
                eq("SM"),
                any(),
                eq("Viewer Microscopy"),
                any(),
                any(),
                any(),
                eq("dicom_server_study-1"),
                eq("dicom_server_patient-1"),
                eq("series-1"),
                eq(1),
                anyString()
        )).thenReturn(501L);
        when(WorklistMapper.updateWorklistReceivedFromCallbackById(
                eq(99L),
                eq(10L),
                eq(501L),
                eq(WorklistStatus.IN_PROGRESS.code()),
                eq(null),
                anyString()
        )).thenReturn(1);
        WorklistDetailRow refreshed = new WorklistDetailRow();
        refreshed.setId(10L);
        refreshed.setHospitalId(99L);
        refreshed.setPatientId(100L);
        refreshed.setStatus(WorklistStatus.IN_PROGRESS.name());
        refreshed.setStudyInstanceUid("1.2.826.0.1.3680043.8.498.1");
        when(WorklistMapper.findWorklistById(99L, 10L)).thenReturn(refreshed);

        ResponseMessage<BaseResult> response = WorklistService.receivedStudy(request, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(WorklistMapper).findWorklistByAccessionNumber("VIEW-SM-0005");
        verify(WorklistMapper).updateWorklistReceivedFromCallbackById(
                eq(99L),
                eq(10L),
                eq(501L),
                eq(WorklistStatus.IN_PROGRESS.code()),
                eq(null),
                anyString()
        );
        verify(WorklistMapper, never()).insertHistory(any(), any(), any(), any(), any(), anyString(), any(), any());
        verify(dicomServerCallbackLogMapper).insertCallbackLog(
                anyString(),
                eq("VIEW-SM-0005"),
                eq("dicom_server_study-1"),
                eq("dicom_server_patient-1"),
                eq("[\"series-1\"]"),
                anyString(),
                eq(true),
                eq(null),
                any(),
                anyString()
        );
        verify(realtimeNotificationService).publishImageReceived(any(WorklistDetailRow.class), eq(501L), any(), anyString());
    }

    @Test
    void receivedStudyShouldRejectWhenDicomServerStudyHasNoInstances() throws Exception {
        WorklistServiceImpl WorklistService = buildService();
        authenticateMachineClient(4L);

        WorklistReceivedStudyRequest request = new WorklistReceivedStudyRequest();
        request.setEvent("STUDY_RECEIVED");
        request.setAccessionNumber("DX-KSFH-0001");
        request.setDicomServerStudyId("dicom_server_empty-study");
        request.setStudyInstanceUid("1.2.826.0.1.3680043.8.498.empty");

        WorklistDetailRow Worklist = new WorklistDetailRow();
        Worklist.setId(22L);
        Worklist.setHospitalId(1L);
        Worklist.setPatientId(33L);
        Worklist.setModalityId(4L);
        Worklist.setDicomServerId(4L);
        Worklist.setAccessionNumber("DX-KSFH-0001");
        Worklist.setStatus(WorklistStatus.IN_PROGRESS.name());
        when(WorklistMapper.findWorklistByAccessionNumber("DX-KSFH-0001")).thenReturn(Worklist);

        HospitalDicomServerResponse server = dicomServer(4L);
        when(dicomServerMapper.findActiveDicomServerByWorklist(1L, 22L)).thenReturn(server);
        when(dicomServerClientService.getStudyById(
                eq("http://localhost:8042"),
                eq("dicom_server"),
                eq("secret"),
                eq("dicom_server_empty-study")
        )).thenReturn(dicomServerStudy("dicom_server_empty-study", "1.2.826.0.1.3680043.8.498.empty", "DX-KSFH-0001", List.of()));

        ResponseMessage<BaseResult> response = WorklistService.receivedStudy(request, new MockHttpServletRequest());

        assertFalse(response.isSuccess());
        verify(WorklistMapper, never()).updateWorklistReceivedFromCallbackById(any(), any(), any(), any(), any(), any());
        verify(realtimeNotificationService, never()).publishImageReceived(any(WorklistDetailRow.class), any(), any(), anyString());
        verify(studyMapper, never()).upsertFromWorklist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(dicomServerCallbackLogMapper).insertCallbackLog(anyString(), eq("DX-KSFH-0001"), eq("dicom_server_empty-study"), anyString(), anyString(), anyString(), eq(false), eq("DicomServer study has no image instances yet."), any(), anyString());
        verifyNoSystemErrorActivity();
    }

    @Test
    void receivedStudyShouldTrustSignedCallbackPayloadWhenItReportsInstances() throws Exception {
        WorklistServiceImpl WorklistService = buildService();
        authenticateMachineClient(4L);

        WorklistReceivedStudyRequest request = new WorklistReceivedStudyRequest();
        request.setEvent("STUDY_RECEIVED");
        request.setAccessionNumber("DX-KSFH-0002");
        request.setDicomServerStudyId("dicom_server_empty-study-2");
        request.setDicomServerPatientId("dicom_server_patient-2");
        request.setDicomServerSeriesIds(List.of("series-2"));
        request.setStudyInstanceUid("1.2.826.0.1.3680043.8.498.empty2");
        request.setStudyDescription("Portable X-Ray");
        request.setImageInstanceCount(12);

        WorklistDetailRow Worklist = new WorklistDetailRow();
        Worklist.setId(23L);
        Worklist.setHospitalId(1L);
        Worklist.setPatientId(34L);
        Worklist.setModalityId(4L);
        Worklist.setDicomServerId(4L);
        Worklist.setAccessionNumber("DX-KSFH-0002");
        Worklist.setStatus(WorklistStatus.IN_PROGRESS.name());
        Worklist.setModalityCode("DX");
        Worklist.setStudyDescription("Portable X-Ray");
        when(WorklistMapper.findWorklistByAccessionNumber("DX-KSFH-0002")).thenReturn(Worklist);

        HospitalDicomServerResponse server = dicomServer(4L);
        when(dicomServerMapper.findActiveDicomServerByWorklist(1L, 23L)).thenReturn(server);
        when(studyMapper.upsertFromWorklist(
                eq(1L),
                eq(34L),
                eq("1.2.826.0.1.3680043.8.498.empty2"),
                eq("DX-KSFH-0002"),
                eq(4L),
                eq("DX"),
                any(),
                eq("Portable X-Ray"),
                any(),
                any(),
                any(),
                eq("dicom_server_empty-study-2"),
                eq("dicom_server_patient-2"),
                eq("series-2"),
                eq(12),
                anyString()
        )).thenReturn(601L);
        when(WorklistMapper.updateWorklistReceivedFromCallbackById(
                eq(1L),
                eq(23L),
                eq(601L),
                eq(WorklistStatus.IN_PROGRESS.code()),
                eq(null),
                anyString()
        )).thenReturn(1);
        WorklistDetailRow refreshed = new WorklistDetailRow();
        refreshed.setId(23L);
        refreshed.setHospitalId(1L);
        refreshed.setPatientId(34L);
        refreshed.setStatus(WorklistStatus.IN_PROGRESS.name());
        refreshed.setStudyInstanceUid("1.2.826.0.1.3680043.8.498.empty2");
        when(WorklistMapper.findWorklistById(1L, 23L)).thenReturn(refreshed);

        ResponseMessage<BaseResult> response = WorklistService.receivedStudy(request, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(dicomServerClientService, never()).getStudyById(anyString(), anyString(), anyString(), anyString());
        verify(WorklistMapper).updateWorklistReceivedFromCallbackById(
                eq(1L),
                eq(23L),
                eq(601L),
                eq(WorklistStatus.IN_PROGRESS.code()),
                eq(null),
                anyString()
        );
    }

    @Test
    void receivedStudyShouldAcceptUploadReferenceAccessionAndKeepGeneratedVisitCode() throws Exception {
        WorklistServiceImpl WorklistService = buildService();
        authenticateMachineClient(4L);

        WorklistReceivedStudyRequest request = new WorklistReceivedStudyRequest();
        request.setEvent("STUDY_RECEIVED");
        request.setAccessionNumber("CT-KSFH-200914-0001");
        request.setDicomServerStudyId("dicom_server_uploaded-study");
        request.setDicomServerPatientId("dicom_server_uploaded-patient");
        request.setDicomServerSeriesIds(List.of("uploaded-series-1"));
        request.setStudyInstanceUid("1.2.826.0.1.3680043.8.498.uploaded");
        request.setStudyDescription("Uploaded CT");
        request.setImageInstanceCount(146);

        WorklistDetailRow Worklist = new WorklistDetailRow();
        Worklist.setId(24L);
        Worklist.setHospitalId(1L);
        Worklist.setPatientId(35L);
        Worklist.setModalityId(4L);
        Worklist.setDicomServerId(4L);
        Worklist.setVisitCode("KSFH-20260614-000001");
        Worklist.setAccessionNumber("KSFH-20260614-000001");
        Worklist.setReferenceVisitCode("CT-KSFH-200914-0001");
        Worklist.setStatus(WorklistStatus.IN_PROGRESS.name());
        Worklist.setModalityCode("CT");
        Worklist.setStudyDescription("Uploaded CT");
        when(WorklistMapper.findWorklistByAccessionNumber("CT-KSFH-200914-0001")).thenReturn(Worklist);

        HospitalDicomServerResponse server = dicomServer(4L);
        when(dicomServerMapper.findActiveDicomServerByWorklist(1L, 24L)).thenReturn(server);
        when(studyMapper.upsertFromWorklist(
                eq(1L),
                eq(35L),
                eq("1.2.826.0.1.3680043.8.498.uploaded"),
                eq("KSFH-20260614-000001"),
                eq(4L),
                eq("CT"),
                any(),
                eq("Uploaded CT"),
                any(),
                any(),
                any(),
                eq("dicom_server_uploaded-study"),
                eq("dicom_server_uploaded-patient"),
                eq("uploaded-series-1"),
                eq(146),
                anyString()
        )).thenReturn(602L);
        when(WorklistMapper.updateWorklistReceivedFromCallbackById(
                eq(1L),
                eq(24L),
                eq(602L),
                eq(WorklistStatus.IN_PROGRESS.code()),
                eq(null),
                anyString()
        )).thenReturn(1);
        WorklistDetailRow refreshed = new WorklistDetailRow();
        refreshed.setId(24L);
        refreshed.setHospitalId(1L);
        refreshed.setPatientId(35L);
        refreshed.setStatus(WorklistStatus.IN_PROGRESS.name());
        refreshed.setStudyInstanceUid("1.2.826.0.1.3680043.8.498.uploaded");
        when(WorklistMapper.findWorklistById(1L, 24L)).thenReturn(refreshed);

        ResponseMessage<BaseResult> response = WorklistService.receivedStudy(request, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(dicomServerClientService, never()).getStudyById(anyString(), anyString(), anyString(), anyString());
        verify(studyMapper).upsertFromWorklist(
                eq(1L),
                eq(35L),
                eq("1.2.826.0.1.3680043.8.498.uploaded"),
                eq("KSFH-20260614-000001"),
                eq(4L),
                eq("CT"),
                any(),
                eq("Uploaded CT"),
                any(),
                any(),
                any(),
                eq("dicom_server_uploaded-study"),
                eq("dicom_server_uploaded-patient"),
                eq("uploaded-series-1"),
                eq(146),
                anyString()
        );
    }

    @Test
    void receivedStudyShouldAcknowledgeDirectStudyUploadWithoutCreatingWorklistStudyLink() throws Exception {
        WorklistServiceImpl WorklistService = buildService();
        authenticateMachineClient(4L);

        WorklistReceivedStudyRequest request = new WorklistReceivedStudyRequest();
        request.setEvent("STUDY_RECEIVED");
        request.setAccessionNumber("DIRECT-CT-404");
        request.setDicomServerStudyId("direct-study-1");
        request.setDicomServerPatientId("direct-patient-1");
        request.setDicomServerSeriesIds(List.of("direct-series-1"));
        request.setStudyInstanceUid("1.2.826.0.1.3680043.8.498.direct");
        request.setImageInstanceCount(12);
        when(WorklistMapper.findWorklistByAccessionNumber("DIRECT-CT-404")).thenReturn(null);
        when(WorklistMapper.findWorklistByStudyIdentifiers("1.2.826.0.1.3680043.8.498.direct", "direct-study-1")).thenReturn(null);
        when(dicomServerMapper.getDicomServerById(4L, null)).thenReturn(List.of(dicomServer(4L)));
        when(dicomServerClientService.getStudyById(
                "http://localhost:8042",
                "dicom_server",
                "secret",
                "direct-study-1"
        )).thenReturn(dicomServerStudy("direct-study-1", "1.2.826.0.1.3680043.8.498.direct", "DIRECT-CT-404", List.of("instance-1")));

        ResponseMessage<BaseResult> response = WorklistService.receivedStudy(request, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(WorklistMapper).findWorklistByStudyIdentifiers("1.2.826.0.1.3680043.8.498.direct", "direct-study-1");
        verify(WorklistMapper, never()).updateWorklistReceivedFromCallbackById(any(), any(), any(), any(), any(), any());
        verify(WorklistMapper, never()).upsertWorklistStudyLink(any(), any(), any(), any());
        verify(studyMapper, never()).upsertFromWorklist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(dicomServerCallbackLogMapper).insertCallbackLog(
                anyString(),
                eq("DIRECT-CT-404"),
                eq("direct-study-1"),
                eq("direct-patient-1"),
                eq("[\"direct-series-1\"]"),
                anyString(),
                eq(true),
                eq(null),
                eq("Callback acknowledged without a matching Worklist. Direct Study uploads are saved by the upload flow."),
                anyString()
        );
    }

    @Test
    void receivedStudyShouldRejectWhenCallbackClientBelongsToDifferentDicomServer() throws Exception {
        WorklistServiceImpl WorklistService = buildService();
        authenticateMachineClient(9L);

        WorklistReceivedStudyRequest request = new WorklistReceivedStudyRequest();
        request.setAccessionNumber("DX-KSFH-0001");

        WorklistDetailRow Worklist = new WorklistDetailRow();
        Worklist.setId(22L);
        Worklist.setHospitalId(1L);
        Worklist.setPatientId(33L);
        Worklist.setModalityId(4L);
        Worklist.setDicomServerId(4L);
        Worklist.setAccessionNumber("DX-KSFH-0001");
        Worklist.setStatus(WorklistStatus.IN_PROGRESS.name());
        when(WorklistMapper.findWorklistByAccessionNumber("DX-KSFH-0001")).thenReturn(Worklist);

        ResponseMessage<BaseResult> response = WorklistService.receivedStudy(request, new MockHttpServletRequest());

        assertFalse(response.isSuccess());
        assertEquals(403, response.getHeader().getStatusCode());
        verify(WorklistMapper, never()).updateWorklistReceivedFromCallbackById(any(), any(), any(), any(), any(), any());
        verify(studyMapper, never()).upsertFromWorklist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(dicomServerCallbackLogMapper).insertCallbackLog(anyString(), eq("DX-KSFH-0001"), anyString(), anyString(), anyString(), anyString(), eq(false), anyString(), any(), anyString());
        verifyNoSystemErrorActivity();
    }

    @Test
    void receivedStudyShouldAcceptCallbackClientAliasWhenDicomEndpointMatchesRoute() throws Exception {
        WorklistServiceImpl WorklistService = buildService();
        authenticateMachineClient(9L);

        WorklistReceivedStudyRequest request = callbackRequest("DX-ALIAS-0001", "1.2.826.alias", "alias-study");
        WorklistDetailRow Worklist = callbackWorklist(29L, WorklistStatus.IN_PROGRESS);
        Worklist.setAccessionNumber("DX-ALIAS-0001");
        when(WorklistMapper.findWorklistByAccessionNumber("DX-ALIAS-0001")).thenReturn(Worklist);
        when(dicomServerMapper.getDicomServerById(9L, null)).thenReturn(List.of(dicomServer(9L)));
        when(dicomServerMapper.getDicomServerById(4L, 1L)).thenReturn(List.of(dicomServer(4L)));
        when(dicomServerMapper.findActiveDicomServerByWorklist(1L, 29L)).thenReturn(dicomServer(4L));
        when(studyMapper.upsertFromWorklist(
                eq(1L), eq(35L), eq("1.2.826.alias"), eq("DX-ALIAS-0001"), eq(4L), eq("DX"),
                any(), eq("Callback test"), any(), eq(4L), any(), eq("alias-study"), eq("callback-patient"),
                eq("callback-series"), eq(2), anyString()
        )).thenReturn(605L);
        when(WorklistMapper.updateWorklistReceivedFromCallbackById(
                eq(1L), eq(29L), eq(605L), eq(WorklistStatus.IN_PROGRESS.code()), eq(null), anyString()
        )).thenReturn(1);
        WorklistDetailRow refreshed = callbackWorklist(29L, WorklistStatus.IN_PROGRESS);
        refreshed.setStudyInstanceUid("1.2.826.alias");
        when(WorklistMapper.findWorklistById(1L, 29L)).thenReturn(refreshed);

        ResponseMessage<BaseResult> response = WorklistService.receivedStudy(request, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(WorklistMapper).updateWorklistReceivedFromCallbackById(
                eq(1L), eq(29L), eq(605L), eq(WorklistStatus.IN_PROGRESS.code()), eq(null), anyString()
        );
        verify(dicomServerCallbackLogMapper).insertCallbackLog(
                anyString(), eq("DX-ALIAS-0001"), eq("alias-study"), eq("callback-patient"),
                eq("[\"callback-series\"]"), anyString(), eq(true), eq(null), any(), anyString()
        );
    }

    @Test
    void receivedStudyShouldMoveWaitingWorklistToInProgressAndInsertHistory() throws Exception {
        WorklistServiceImpl WorklistService = buildService();
        authenticateMachineClient(4L);

        WorklistReceivedStudyRequest request = callbackRequest("DX-WAITING-0001", "1.2.826.waiting", "waiting-study");
        WorklistDetailRow Worklist = callbackWorklist(25L, WorklistStatus.WAITING);
        Worklist.setAccessionNumber("DX-WAITING-0001");
        when(WorklistMapper.findWorklistByAccessionNumber("DX-WAITING-0001")).thenReturn(Worklist);
        when(dicomServerMapper.findActiveDicomServerByWorklist(1L, 25L)).thenReturn(dicomServer(4L));
        when(studyMapper.upsertFromWorklist(
                eq(1L), eq(35L), eq("1.2.826.waiting"), eq("DX-WAITING-0001"), eq(4L), eq("DX"),
                any(), eq("Callback test"), any(), eq(4L), any(), eq("waiting-study"), eq("callback-patient"),
                eq("callback-series"), eq(2), anyString()
        )).thenReturn(603L);
        when(WorklistMapper.updateWorklistReceivedFromCallbackById(
                eq(1L), eq(25L), eq(603L), eq(WorklistStatus.IN_PROGRESS.code()), eq(null), anyString()
        )).thenReturn(1);
        WorklistDetailRow refreshed = callbackWorklist(25L, WorklistStatus.IN_PROGRESS);
        refreshed.setStudyInstanceUid("1.2.826.waiting");
        when(WorklistMapper.findWorklistById(1L, 25L)).thenReturn(refreshed);

        ResponseMessage<BaseResult> response = WorklistService.receivedStudy(request, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(WorklistMapper).insertHistory(
                eq(1L), eq(25L), eq(35L), eq(WorklistStatus.WAITING.code()), eq(WorklistStatus.IN_PROGRESS.code()),
                eq(WorklistConstants.ACTION_RECEIVED_STUDY), anyString(), eq(null)
        );
    }

    @Test
    void receivedStudyShouldRecoverFailedWorklistAndInsertHistory() throws Exception {
        WorklistServiceImpl WorklistService = buildService();
        authenticateMachineClient(4L);

        WorklistReceivedStudyRequest request = callbackRequest("DX-FAILED-0001", "1.2.826.failed", "failed-study");
        WorklistDetailRow Worklist = callbackWorklist(26L, WorklistStatus.FAILED);
        Worklist.setAccessionNumber("DX-FAILED-0001");
        when(WorklistMapper.findWorklistByAccessionNumber("DX-FAILED-0001")).thenReturn(Worklist);
        when(dicomServerMapper.findActiveDicomServerByWorklist(1L, 26L)).thenReturn(dicomServer(4L));
        when(studyMapper.upsertFromWorklist(
                eq(1L), eq(35L), eq("1.2.826.failed"), eq("DX-FAILED-0001"), eq(4L), eq("DX"),
                any(), eq("Callback test"), any(), eq(4L), any(), eq("failed-study"), eq("callback-patient"),
                eq("callback-series"), eq(2), anyString()
        )).thenReturn(604L);
        when(WorklistMapper.updateWorklistReceivedFromCallbackById(
                eq(1L), eq(26L), eq(604L), eq(WorklistStatus.IN_PROGRESS.code()), eq(null), anyString()
        )).thenReturn(1);
        WorklistDetailRow refreshed = callbackWorklist(26L, WorklistStatus.IN_PROGRESS);
        refreshed.setStudyInstanceUid("1.2.826.failed");
        when(WorklistMapper.findWorklistById(1L, 26L)).thenReturn(refreshed);

        ResponseMessage<BaseResult> response = WorklistService.receivedStudy(request, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(WorklistMapper).insertHistory(
                eq(1L), eq(26L), eq(35L), eq(WorklistStatus.FAILED.code()), eq(WorklistStatus.IN_PROGRESS.code()),
                eq(WorklistConstants.ACTION_RECEIVED_STUDY), anyString(), eq(null)
        );
    }

    @Test
    void receivedStudyShouldAcknowledgeCancelledWorklistWithoutChangingStudy() throws Exception {
        WorklistServiceImpl WorklistService = buildService();
        authenticateMachineClient(4L);

        WorklistReceivedStudyRequest request = callbackRequest("DX-CANCELLED-0001", "1.2.826.cancelled", "cancelled-study");
        WorklistDetailRow Worklist = callbackWorklist(27L, WorklistStatus.CANCELLED);
        Worklist.setAccessionNumber("DX-CANCELLED-0001");
        when(WorklistMapper.findWorklistByAccessionNumber("DX-CANCELLED-0001")).thenReturn(Worklist);

        ResponseMessage<BaseResult> response = WorklistService.receivedStudy(request, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(studyMapper, never()).upsertFromWorklist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(WorklistMapper, never()).updateWorklistReceivedFromCallbackById(any(), any(), any(), any(), any(), any());
        verify(WorklistMapper, never()).insertHistory(any(), any(), any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void receivedStudyShouldRejectMismatchedAccessionResolvedByVisitCode() throws Exception {
        WorklistServiceImpl WorklistService = buildService();
        authenticateMachineClient(4L);

        WorklistReceivedStudyRequest request = callbackRequest("WRONG-ACCESSION", "1.2.826.mismatch", "mismatch-study");
        request.setVisitCode("DX-VISIT-0001");
        WorklistDetailRow Worklist = callbackWorklist(28L, WorklistStatus.IN_PROGRESS);
        Worklist.setVisitCode("DX-VISIT-0001");
        Worklist.setAccessionNumber("DX-CORRECT-0001");
        when(WorklistMapper.findWorklistByAccessionNumber("WRONG-ACCESSION")).thenReturn(null);
        when(WorklistMapper.findWorklistByVisitCodeAnyHospital("DX-VISIT-0001")).thenReturn(Worklist);
        when(dicomServerMapper.findActiveDicomServerByWorklist(1L, 28L)).thenReturn(dicomServer(4L));

        ResponseMessage<BaseResult> response = WorklistService.receivedStudy(request, new MockHttpServletRequest());

        assertFalse(response.isSuccess());
        verify(studyMapper, never()).upsertFromWorklist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(WorklistMapper, never()).updateWorklistReceivedFromCallbackById(any(), any(), any(), any(), any(), any());
        verify(dicomServerCallbackLogMapper).insertCallbackLog(
                anyString(), eq("WRONG-ACCESSION"), eq("mismatch-study"), eq("callback-patient"),
                eq("[\"callback-series\"]"), anyString(), eq(false), eq("Callback accession does not match this Worklist."), any(), anyString()
        );
        verifyNoSystemErrorActivity();
    }

    private void verifyNoSystemErrorActivity() throws Exception {
        verify(activityLogService, never()).insert(
                anyString(),
                any(),
                any(),
                anyString(),
                anyString(),
                anyString(),
                eq(WorklistConstants.LOG_STATUS_ERROR),
                anyString(),
                any(LocalTime.class),
                any(LocalTime.class),
                any(HttpServletRequest.class)
        );
    }

    private static WorklistReceivedStudyRequest callbackRequest(String accessionNumber, String studyInstanceUid, String dicomServerStudyId) {
        WorklistReceivedStudyRequest request = new WorklistReceivedStudyRequest();
        request.setEvent("STUDY_RECEIVED");
        request.setAccessionNumber(accessionNumber);
        request.setDicomServerStudyId(dicomServerStudyId);
        request.setDicomServerPatientId("callback-patient");
        request.setDicomServerSeriesIds(List.of("callback-series"));
        request.setStudyInstanceUid(studyInstanceUid);
        request.setStudyDescription("Callback test");
        request.setStudyDate("20260615");
        request.setImageInstanceCount(2);
        return request;
    }

    private static WorklistDetailRow callbackWorklist(Long id, WorklistStatus status) {
        WorklistDetailRow Worklist = new WorklistDetailRow();
        Worklist.setId(id);
        Worklist.setHospitalId(1L);
        Worklist.setPatientId(35L);
        Worklist.setModalityId(4L);
        Worklist.setModalityCode("DX");
        Worklist.setDicomServerId(4L);
        Worklist.setStudyDescription("Callback test");
        Worklist.setStatus(status.name());
        return Worklist;
    }

    private void authenticateMachineClient() {
        authenticateMachineClient(null);
    }

    private void authenticateMachineClient(Long dicomServerId) {
        Jwt.Builder jwtBuilder = Jwt.withTokenValue("machine-token")
                .header("alg", "none")
                .claim("principalType", ApiConstants.Security.PRINCIPAL_TYPE_CLIENT)
                .claim("client_id", "pacs-adapter")
                .claim("clientId", "pacs-adapter");
        if (dicomServerId != null) {
            jwtBuilder.claim("dicomServerId", dicomServerId);
        }
        Jwt jwt = jwtBuilder.build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, List.of(), "pacs-adapter");
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static HospitalDicomServerResponse dicomServer(Long id) {
        HospitalDicomServerResponse server = new HospitalDicomServerResponse();
        server.setId(id);
        server.setHospitalId(1L);
        server.setName("DicomServer KSFH");
        server.setBaseUrl("http://localhost:8042");
        server.setUsername("dicom_server");
        server.setPassword("secret");
        server.setIsActive(1L);
        return server;
    }

    private static DicomServerStudyResponse dicomServerStudy(String id, String studyInstanceUid, String accessionNumber, List<String> instances) {
        DicomServerStudyResponse study = new DicomServerStudyResponse();
        study.setId(id);
        study.setParentPatient("dicom_server_patient-1");
        study.setSeries(List.of("series-1"));
        study.setInstances(instances);
        study.setMainDicomTags(Map.of(
                "StudyInstanceUID", studyInstanceUid,
                "AccessionNumber", accessionNumber,
                "StudyDescription", "Viewer Microscopy",
                "StudyDate", "20260524"
        ));
        study.setPatientMainDicomTags(Map.of("PatientID", "PAT-1", "PatientName", "Viewer Microscopy"));
        return study;
    }
}
