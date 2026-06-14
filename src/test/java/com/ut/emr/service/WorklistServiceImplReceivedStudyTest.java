package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.config.ApiConstants;
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
                eq("dicom_server_study-1"),
                eq("dicom_server_patient-1"),
                eq("series-1"),
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
        verify(studyMapper, never()).upsertFromWorklist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(dicomServerCallbackLogMapper).insertCallbackLog(anyString(), eq("DX-KSFH-0001"), eq("dicom_server_empty-study"), anyString(), anyString(), anyString(), eq(false), eq("DicomServer study has no image instances yet."), any(), anyString());
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
                eq("dicom_server_empty-study-2"),
                eq("dicom_server_patient-2"),
                eq("series-2"),
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
    void receivedStudyShouldAcceptWhenWorklistIsMissing() throws Exception {
        WorklistServiceImpl WorklistService = buildService();
        authenticateMachineClient();

        WorklistReceivedStudyRequest request = new WorklistReceivedStudyRequest();
        request.setAccessionNumber("VIEW-SM-404");
        when(WorklistMapper.findWorklistByAccessionNumber("VIEW-SM-404")).thenReturn(null);

        ResponseMessage<BaseResult> response = WorklistService.receivedStudy(request, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(WorklistMapper).findWorklistByAccessionNumber("VIEW-SM-404");
        verify(WorklistMapper, never()).updateWorklistReceivedFromCallbackById(any(), any(), any(), any(), any(), any());
        verify(dicomServerCallbackLogMapper).insertCallbackLog(anyString(), eq("VIEW-SM-404"), anyString(), anyString(), anyString(), anyString(), eq(false), anyString(), any(), anyString());
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
        verify(studyMapper, never()).upsertFromWorklist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(dicomServerCallbackLogMapper).insertCallbackLog(anyString(), eq("DX-KSFH-0001"), anyString(), anyString(), anyString(), anyString(), eq(false), anyString(), any(), anyString());
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
