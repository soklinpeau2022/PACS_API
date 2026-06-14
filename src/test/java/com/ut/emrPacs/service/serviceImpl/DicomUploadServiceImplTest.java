package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.mapper.hospital.HospitalMapper;
import com.ut.emrPacs.mapper.modality.ModalityMapper;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.mapper.pacs.PatientMapper;
import com.ut.emrPacs.mapper.pacs.StudyMapper;
import com.ut.emrPacs.mapper.pacs.WorklistMapper;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.dto.request.pacs.dicomUpload.DicomUploadRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerInstanceUploadResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerSeriesResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerStudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomUpload.DicomUploadResponse;
import com.ut.emrPacs.model.dto.response.pacs.patient.PatientResponse;
import com.ut.emrPacs.model.dto.response.pacs.study.StudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import com.ut.emrPacs.model.dto.response.systemSettings.modality.ModalityResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DicomServerClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DicomUploadServiceImplTest {

    @Mock
    private DicomServerClientService dicomServerClientService;
    @Mock
    private DicomServerMapper dicomServerMapper;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private StudyMapper studyMapper;
    @Mock
    private WorklistMapper worklistMapper;
    @Mock
    private ModalityMapper modalityMapper;
    @Mock
    private HospitalMapper hospitalMapper;
    @Mock
    private PublicEntityKeyResolver publicEntityKeyResolver;
    @Mock
    private ActivityLogService activityLogService;

    private DicomUploadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DicomUploadServiceImpl();
        ReflectionTestUtils.setField(service, "dicomServerClientService", dicomServerClientService);
        ReflectionTestUtils.setField(service, "dicomServerMapper", dicomServerMapper);
        ReflectionTestUtils.setField(service, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(service, "studyMapper", studyMapper);
        ReflectionTestUtils.setField(service, "worklistMapper", worklistMapper);
        ReflectionTestUtils.setField(service, "modalityMapper", modalityMapper);
        ReflectionTestUtils.setField(service, "hospitalMapper", hospitalMapper);
        ReflectionTestUtils.setField(service, "publicEntityKeyResolver", publicEntityKeyResolver);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());
        ReflectionTestUtils.setField(service, "activityLogService", activityLogService);
        ReflectionTestUtils.setField(service, "maxDicomUploadRequestBytes", 4L * 1024L * 1024L * 1024L);
        ReflectionTestUtils.setField(service, "maxZipEntryBytes", 4L * 1024L * 1024L * 1024L);
        ReflectionTestUtils.setField(service, "dicomUploadTempDir", System.getProperty("java.io.tmpdir"));

        lenient().when(publicEntityKeyResolver.resolve(any(PublicEntityKeyResolver.Entity.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        TestingAuthenticationToken auth = new TestingAuthenticationToken("admin", "n/a", "ROLE_ADMIN");
        auth.setAuthenticated(true);
        auth.setDetails(new CurrentUserPrincipal(99L, "admin", 11L, "HSP001", "pacs-web", "jti-1", 1L));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void singleDicomFileCreatesLinkedStudyAndReceivedWorklist() throws Exception {
        HospitalDicomServerResponse server = server();
        when(dicomServerMapper.listActiveDicomServersByHospital(11L)).thenReturn(List.of(server));
        when(dicomServerClientService.uploadInstance(eq("http://dicom.local"), eq("u"), eq("p"), any(), eq(4L)))
                .thenReturn(uploadResponse());
        when(dicomServerClientService.getStudyById(eq("http://dicom.local"), eq("u"), eq("p"), eq("orthanc-study-1")))
                .thenReturn(studyResponse());
        when(dicomServerClientService.getSeriesByStudyId(eq("http://dicom.local"), eq("u"), eq("p"), eq("orthanc-study-1")))
                .thenReturn(List.of(seriesResponse()));
        when(modalityMapper.findActiveHospitalModalityByDicomCode(11L, "CT")).thenReturn(modality());
        when(patientMapper.findByDemographics(eq(11L), eq("HEL"), eq("SOK"), eq(LocalDate.of(1975, 1, 1)), eq("M")))
                .thenReturn(null);
        when(patientMapper.existsPatientSequenceTable()).thenReturn(true);
        when(patientMapper.nextPatientSequenceByYear(eq(11L), any(), any())).thenReturn(1L);
        when(patientMapper.create(eq(11L), any())).thenReturn(55L);
        when(patientMapper.findById(11L, 55L)).thenReturn(patient());
        when(worklistMapper.findWorklistByStudyIdentifiersAndHospital(11L, "1.2.3.4", "orthanc-study-1")).thenReturn(null);
        when(worklistMapper.nextVisitSequence(eq(11L), any())).thenReturn(7L);
        when(worklistMapper.findWorklistByVisitCodeAnyHospital(any())).thenReturn(null);
        when(worklistMapper.assignWorklist(eq(11L), eq(99L), any(), any())).thenReturn(true);
        when(worklistMapper.findWorklistByVisitCode(eq(11L), any())).thenReturn(worklist(66L, null));
        when(studyMapper.upsertFromDicomUpload(eq(11L), eq(55L), eq("1.2.3.4"), any(), eq("ACC-2023"), eq(3L), eq("CT"),
                eq(LocalDate.of(2023, 9, 26)), eq("CT CHEST"), eq(5L), eq(1), eq("orthanc-study-1"), eq("orthanc-patient-1"),
                eq("series-1"), eq(3), eq(99L), any())).thenReturn(77L);
        when(worklistMapper.updateWorklistReceivedByVisitCode(eq(11L), any(), eq(77L), any(), eq(2), eq(99L))).thenReturn(1);
        when(worklistMapper.findWorklistById(11L, 66L)).thenReturn(worklist(66L, 77L));
        when(studyMapper.findById(11L, 77L)).thenReturn(savedStudy());

        ResponseMessage<BaseResult> response = service.uploadDicom(new DicomUploadRequest(), List.of(
                new MockMultipartFile("files", "one.dcm", "application/dicom", new byte[] {1, 2, 3, 4})
        ), null, new MockHttpServletRequest());

        assertTrue(response.getHeader().getResult());
        DicomUploadResponse body = (DicomUploadResponse) response.getBody().getData().get(0);
        assertEquals(1, body.getAcceptedFiles());
        assertEquals(0, body.getFailedFiles());
        assertEquals(1, body.getStudyCount());
        assertEquals("study-key", body.getStudies().get(0).getStudyPublicKey());
        assertEquals("worklist-key", body.getStudies().get(0).getWorklistPublicKey());
        verify(worklistMapper).upsertWorklistStudyLink(11L, 66L, 77L, 99L);
    }

    @Test
    void acceptedDicomFileFailsWhenStudyCannotBeLinkedToWorklist() throws Exception {
        HospitalDicomServerResponse server = server();
        when(dicomServerMapper.listActiveDicomServersByHospital(11L)).thenReturn(List.of(server));
        when(dicomServerClientService.uploadInstance(eq("http://dicom.local"), eq("u"), eq("p"), any(), eq(4L)))
                .thenReturn(uploadResponse());
        when(dicomServerClientService.getStudyById(eq("http://dicom.local"), eq("u"), eq("p"), eq("orthanc-study-1")))
                .thenReturn(studyResponse());
        when(dicomServerClientService.getSeriesByStudyId(eq("http://dicom.local"), eq("u"), eq("p"), eq("orthanc-study-1")))
                .thenReturn(List.of(seriesResponse()));
        when(modalityMapper.findActiveHospitalModalityByDicomCode(11L, "CT")).thenReturn(modality());
        when(patientMapper.findByDemographics(eq(11L), eq("HEL"), eq("SOK"), eq(LocalDate.of(1975, 1, 1)), eq("M")))
                .thenReturn(patient());
        when(worklistMapper.findWorklistByStudyIdentifiersAndHospital(11L, "1.2.3.4", "orthanc-study-1")).thenReturn(null);
        when(worklistMapper.nextVisitSequence(eq(11L), any())).thenReturn(7L);
        when(worklistMapper.findWorklistByVisitCodeAnyHospital(any())).thenReturn(null);
        when(worklistMapper.assignWorklist(eq(11L), eq(99L), any(), any())).thenReturn(true);
        when(worklistMapper.findWorklistByVisitCode(eq(11L), any())).thenReturn(worklist(66L, null));
        when(studyMapper.upsertFromDicomUpload(eq(11L), eq(55L), eq("1.2.3.4"), any(), eq("ACC-2023"), eq(3L), eq("CT"),
                eq(LocalDate.of(2023, 9, 26)), eq("CT CHEST"), eq(5L), eq(1), eq("orthanc-study-1"), eq("orthanc-patient-1"),
                eq("series-1"), eq(3), eq(99L), any())).thenReturn(77L);
        when(worklistMapper.updateWorklistReceivedByVisitCode(eq(11L), any(), eq(77L), any(), eq(2), eq(99L))).thenReturn(0);
        when(worklistMapper.updateWorklistReceivedById(eq(11L), eq(66L), eq(77L), eq(2), eq(99L), any())).thenReturn(0);

        ResponseMessage<BaseResult> response = service.uploadDicom(new DicomUploadRequest(), List.of(
                new MockMultipartFile("files", "one.dcm", "application/dicom", new byte[] {1, 2, 3, 4})
        ), null, new MockHttpServletRequest());

        assertFalse(response.getHeader().getResult());
        DicomUploadResponse body = (DicomUploadResponse) response.getBody().getData().get(0);
        assertEquals(0, body.getAcceptedFiles());
        assertEquals(1, body.getFailedFiles());
        assertNotNull(body.getErrors());
        assertTrue(body.getErrors().get(0).contains("Worklist could not be marked"));
    }

    private static HospitalDicomServerResponse server() {
        HospitalDicomServerResponse server = new HospitalDicomServerResponse();
        server.setId(5L);
        server.setPublicKey("server-key");
        server.setName("KSFH");
        server.setHospitalPublicKey("hospital-key");
        server.setHospitalName("KSFH Hospital");
        server.setBaseUrl("http://dicom.local");
        server.setUsername("u");
        server.setPassword("p");
        return server;
    }

    private static DicomServerInstanceUploadResponse uploadResponse() {
        DicomServerInstanceUploadResponse response = new DicomServerInstanceUploadResponse();
        response.setParentPatient("orthanc-patient-1");
        response.setParentStudy("orthanc-study-1");
        response.setParentSeries("series-1");
        return response;
    }

    private static DicomServerStudyResponse studyResponse() {
        DicomServerStudyResponse response = new DicomServerStudyResponse();
        response.setId("orthanc-study-1");
        response.setParentPatient("orthanc-patient-1");
        response.setMainDicomTags(Map.of(
                "StudyInstanceUID", "1.2.3.4",
                "Modality", "CT",
                "AccessionNumber", "ACC-2023",
                "StudyDate", "20230926",
                "StudyDescription", "CT CHEST"
        ));
        response.setPatientMainDicomTags(Map.of(
                "PatientID", "PID-HEL",
                "PatientName", "SOK^HEL",
                "PatientBirthDate", "19750101",
                "PatientSex", "M"
        ));
        response.setInstances(List.of("i1", "i2", "i3"));
        response.setSeries(List.of("series-1"));
        return response;
    }

    private static DicomServerSeriesResponse seriesResponse() {
        DicomServerSeriesResponse response = new DicomServerSeriesResponse();
        response.setId("series-1");
        response.setInstances(List.of("i1", "i2", "i3"));
        response.setMainDicomTags(Map.of("Modality", "CT"));
        return response;
    }

    private static ModalityResponse modality() {
        ModalityResponse response = new ModalityResponse();
        response.setId(3L);
        response.setPublicKey("modality-key");
        response.setAbbr("CT");
        response.setName("CT");
        return response;
    }

    private static PatientResponse patient() {
        PatientResponse response = new PatientResponse();
        response.setId(55L);
        response.setPublicKey("patient-key");
        response.setPatientCode("26-KSFH-P0000001");
        response.setFirstName("HEL");
        response.setLastName("SOK");
        response.setGender("M");
        response.setDateOfBirth(LocalDate.of(1975, 1, 1));
        return response;
    }

    private static WorklistDetailRow worklist(Long id, Long studyId) {
        WorklistDetailRow response = new WorklistDetailRow();
        response.setId(id);
        response.setPublicKey("worklist-key");
        response.setVisitCode("CT-KSFH-260614-0007");
        response.setPatientId(55L);
        response.setStudyId(studyId);
        return response;
    }

    private static StudyResponse savedStudy() {
        StudyResponse response = new StudyResponse();
        response.setId(77L);
        response.setPublicKey("study-key");
        response.setStudyInstanceUid("1.2.3.4");
        response.setStudyDescription("CT CHEST");
        response.setStatus("IMAGE_RECEIVED");
        return response;
    }
}
