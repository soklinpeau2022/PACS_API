package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.SecurityIncidentReporter;
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
import com.ut.emrPacs.model.dto.response.systemSettings.modality.ModalityResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DicomServerClientService;
import com.ut.emrPacs.service.service.DicomUploadProgressListener;
import com.ut.emrPacs.service.service.RealtimeNotificationService;
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
import org.springframework.web.client.ResourceAccessException;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DicomUploadServiceImplTest {

    @TempDir
    private Path tempDir;

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
    @Mock
    private RealtimeNotificationService realtimeNotificationService;
    @Mock
    private SecurityIncidentReporter securityIncidentReporter;

    private DicomUploadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DicomUploadServiceImpl();
        ReflectionTestUtils.setField(service, "dicomServerClientService", dicomServerClientService);
        ReflectionTestUtils.setField(service, "dicomServerMapper", dicomServerMapper);
        ReflectionTestUtils.setField(service, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(service, "studyMapper", studyMapper);
        ReflectionTestUtils.setField(service, "modalityMapper", modalityMapper);
        ReflectionTestUtils.setField(service, "hospitalMapper", hospitalMapper);
        ReflectionTestUtils.setField(service, "publicEntityKeyResolver", publicEntityKeyResolver);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());
        ReflectionTestUtils.setField(service, "activityLogService", activityLogService);
        ReflectionTestUtils.setField(service, "realtimeNotificationService", realtimeNotificationService);
        ReflectionTestUtils.setField(service, "securityIncidentReporter", securityIncidentReporter);
        ReflectionTestUtils.setField(service, "maxDicomUploadRequestBytes", 4L * 1024L * 1024L * 1024L);
        ReflectionTestUtils.setField(service, "maxZipEntryBytes", 4L * 1024L * 1024L * 1024L);
        ReflectionTestUtils.setField(service, "maxZipEntries", 20000);
        ReflectionTestUtils.setField(service, "instanceUploadMaxAttempts", 3);
        ReflectionTestUtils.setField(service, "instanceUploadRetryBackoffMs", 0L);
        ReflectionTestUtils.setField(service, "instanceUploadParallelism", 2);
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
    void configuredInstanceUploadParallelismAllowsFastBoundedForwarding() {
        ReflectionTestUtils.setField(service, "instanceUploadParallelism", 200);
        assertEquals(Integer.valueOf(200), ReflectionTestUtils.invokeMethod(service, "configuredInstanceUploadParallelism"));

        ReflectionTestUtils.setField(service, "instanceUploadParallelism", 999);
        assertEquals(Integer.valueOf(400), ReflectionTestUtils.invokeMethod(service, "configuredInstanceUploadParallelism"));

        ReflectionTestUtils.setField(service, "instanceUploadParallelism", 0);
        assertEquals(Integer.valueOf(200), ReflectionTestUtils.invokeMethod(service, "configuredInstanceUploadParallelism"));
    }

    @Test
    void singleDicomFileCreatesStudyWithoutWorklist() throws Exception {
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
        when(studyMapper.upsertFromDicomUpload(eq(11L), eq(55L), eq("1.2.3.4"), eq("ACC-2023"), eq("ACC-2023"), eq(3L), eq("CT"),
                eq(LocalDate.of(2023, 9, 26)), eq("CT CHEST"), eq("TSNH HOSPITAL"), eq(5L), eq(1), eq("orthanc-study-1"), eq("orthanc-patient-1"),
                eq("series-1"), eq(3), eq(99L), any())).thenReturn(77L);
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
        assertNull(body.getStudies().get(0).getWorklistPublicKey());
        assertFalse(body.getStudies().get(0).getWorklistCreated());
        assertEquals("PID-HEL", body.getStudies().get(0).getPatientHn());
        assertEquals("TSNH HOSPITAL", body.getStudies().get(0).getInstitutionName());
        verifyNoInteractions(worklistMapper);
        verify(realtimeNotificationService).publishImageReceived(any(StudyResponse.class), any());
    }

    @Test
    void acceptedDicomFileReportsMetadataSyncFailureWithoutMislabelingInstanceAsFailed() throws Exception {
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
        when(studyMapper.upsertFromDicomUpload(eq(11L), eq(55L), eq("1.2.3.4"), eq("ACC-2023"), eq("ACC-2023"), eq(3L), eq("CT"),
                eq(LocalDate.of(2023, 9, 26)), eq("CT CHEST"), eq("TSNH HOSPITAL"), eq(5L), eq(1), eq("orthanc-study-1"), eq("orthanc-patient-1"),
                eq("series-1"), eq(3), eq(99L), any())).thenReturn(null);

        ResponseMessage<BaseResult> response = service.uploadDicom(new DicomUploadRequest(), List.of(
                new MockMultipartFile("files", "one.dcm", "application/dicom", new byte[] {1, 2, 3, 4})
        ), null, new MockHttpServletRequest());

        assertTrue(response.getHeader().getResult());
        DicomUploadResponse body = (DicomUploadResponse) response.getBody().getData().get(0);
        assertEquals(1, body.getAcceptedFiles());
        assertEquals(0, body.getFailedFiles());
        assertEquals(1, body.getMetadataSyncFailures());
        assertNotNull(body.getErrors());
        assertTrue(body.getErrors().get(0).contains("instance accepted"));
        assertTrue(body.getErrors().get(0).contains("study was not saved"));
        verifyNoInteractions(worklistMapper);
    }

    @Test
    void oversizedUploadReportsSecurityIncidentBeforeProcessing() throws Exception {
        ReflectionTestUtils.setField(service, "maxDicomUploadRequestBytes", 2L);
        when(dicomServerMapper.listActiveDicomServersByHospital(11L)).thenReturn(List.of(server()));
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseMessage<BaseResult> response = service.uploadDicom(new DicomUploadRequest(), List.of(
                new MockMultipartFile("files", "big.dcm", "application/dicom", new byte[] {1, 2, 3, 4})
        ), null, request);

        assertFalse(response.getHeader().getResult());
        verify(securityIncidentReporter).reportBlockedRequest(
                request,
                "dicom_upload_policy",
                "request_too_large",
                "4/2"
        );
    }

    @Test
    void unsafeZipEntryNameReportsSecurityIncident() throws Exception {
        when(dicomServerMapper.listActiveDicomServersByHospital(11L)).thenReturn(List.of(server()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockMultipartFile zip = new MockMultipartFile(
                "zipFile",
                "attack.zip",
                "application/zip",
                zipBytes("../evil.dcm", new byte[] {1, 2, 3, 4})
        );

        ResponseMessage<BaseResult> response = service.uploadDicom(new DicomUploadRequest(), null, zip, request);

        assertFalse(response.getHeader().getResult());
        verify(securityIncidentReporter).reportBlockedRequest(
                request,
                "dicom_upload_zip",
                "unsafe_entry_name",
                "../evil.dcm"
        );
    }

    @Test
    void oversizedZipEntryReportsSecurityIncident() throws Exception {
        ReflectionTestUtils.setField(service, "maxZipEntryBytes", 2L);
        when(dicomServerMapper.listActiveDicomServersByHospital(11L)).thenReturn(List.of(server()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockMultipartFile zip = new MockMultipartFile(
                "zipFile",
                "large-entry.zip",
                "application/zip",
                zipBytes("entry.dcm", new byte[] {1, 2, 3, 4})
        );

        ResponseMessage<BaseResult> response = service.uploadDicom(new DicomUploadRequest(), null, zip, request);

        assertFalse(response.getHeader().getResult());
        verify(securityIncidentReporter).reportBlockedRequest(
                request,
                "dicom_upload_zip",
                "entry_too_large",
                "entry.dcm 4/2"
        );
    }

    @Test
    void zipEntryRejectedByDicomServerReturnsUploadResultWithoutThrowing() throws Exception {
        when(dicomServerMapper.listActiveDicomServersByHospital(11L)).thenReturn(List.of(server()));
        when(dicomServerClientService.uploadInstance(eq("http://dicom.local"), eq("u"), eq("p"), any(), eq(4L)))
                .thenThrow(new IllegalStateException("DICOM server upload failed with HTTP 400."));
        MockMultipartFile zip = new MockMultipartFile(
                "zipFile",
                "bad-dicom.zip",
                "application/zip",
                zipBytes("entry.dcm", new byte[] {1, 2, 3, 4})
        );

        ResponseMessage<BaseResult> response = service.uploadDicom(new DicomUploadRequest(), null, zip, new MockHttpServletRequest());

        assertFalse(response.getHeader().getResult());
        DicomUploadResponse body = (DicomUploadResponse) response.getBody().getData().get(0);
        assertEquals(0, body.getAcceptedFiles());
        assertEquals(1, body.getFailedFiles());
        assertTrue(body.getErrors().get(0).contains("entry.dcm"));
        assertTrue(body.getErrors().get(0).contains("HTTP 400"));
        verify(dicomServerClientService, times(1))
                .uploadInstance(eq("http://dicom.local"), eq("u"), eq("p"), any(), eq(4L));
        verifyNoInteractions(worklistMapper);
    }

    @Test
    void transientDicomServerFailureRetriesSameInstanceUntilAccepted() throws Exception {
        when(dicomServerMapper.listActiveDicomServersByHospital(11L)).thenReturn(List.of(server()));
        when(dicomServerClientService.uploadInstance(eq("http://dicom.local"), eq("u"), eq("p"), any(), eq(4L)))
                .thenThrow(new ResourceAccessException("connection reset"))
                .thenReturn(uploadResponse());
        stubSuccessfulStudyPersistence();

        ResponseMessage<BaseResult> response = service.uploadDicom(new DicomUploadRequest(), List.of(
                new MockMultipartFile("files", "retry.dcm", "application/dicom", new byte[] {1, 2, 3, 4})
        ), null, new MockHttpServletRequest());

        assertTrue(response.getHeader().getResult());
        DicomUploadResponse body = (DicomUploadResponse) response.getBody().getData().get(0);
        assertEquals(1, body.getAcceptedFiles());
        assertEquals(0, body.getFailedFiles());
        verify(dicomServerClientService, times(2))
                .uploadInstance(eq("http://dicom.local"), eq("u"), eq("p"), any(), eq(4L));
    }

    @Test
    void dicomDirAndKnownPackageMetadataAreSkippedInsteadOfReportedAsFailedInstances() throws Exception {
        when(dicomServerMapper.listActiveDicomServersByHospital(11L)).thenReturn(List.of(server()));
        MockMultipartFile zip = new MockMultipartFile(
                "zipFile",
                "media.zip",
                "application/zip",
                zipBytes("DICOMDIR", new byte[] {1, 2, 3, 4})
        );

        ResponseMessage<BaseResult> response = service.uploadDicom(new DicomUploadRequest(), null, zip, new MockHttpServletRequest());

        assertFalse(response.getHeader().getResult());
        DicomUploadResponse body = (DicomUploadResponse) response.getBody().getData().get(0);
        assertEquals(0, body.getAcceptedFiles());
        assertEquals(0, body.getFailedFiles());
        assertEquals(1, body.getSkippedFiles());
        verifyNoInteractions(dicomServerClientService);
    }

    @Test
    void badFormatZipEntryRejectedByDicomServerIsSkippedInsteadOfReportedAsFailedInstance() throws Exception {
        when(dicomServerMapper.listActiveDicomServersByHospital(11L)).thenReturn(List.of(server()));
        when(dicomServerClientService.uploadInstance(eq("http://dicom.local"), eq("u"), eq("p"), any(), eq(4L)))
                .thenThrow(new IllegalStateException("DICOM server upload failed with HTTP 400: Bad file format: Cannot parse an invalid DICOM file."));
        MockMultipartFile zip = new MockMultipartFile(
                "zipFile",
                "bad-sidecar.zip",
                "application/zip",
                zipBytes("sidecar-without-extension", new byte[] {1, 2, 3, 4})
        );

        ResponseMessage<BaseResult> response = service.uploadDicom(new DicomUploadRequest(), null, zip, new MockHttpServletRequest());

        assertFalse(response.getHeader().getResult());
        DicomUploadResponse body = (DicomUploadResponse) response.getBody().getData().get(0);
        assertEquals(0, body.getAcceptedFiles());
        assertEquals(0, body.getFailedFiles());
        assertEquals(1, body.getSkippedFiles());
        assertTrue(body.getErrors().isEmpty());
    }

    @Test
    void chunkCompletionReportsRealProgressAndForwardsInstancesInParallel() throws Exception {
        when(dicomServerMapper.listActiveDicomServersByHospital(11L)).thenReturn(List.of(server()));
        CountDownLatch bothUploadsStarted = new CountDownLatch(2);
        AtomicInteger activeUploads = new AtomicInteger();
        AtomicInteger maxActiveUploads = new AtomicInteger();
        when(dicomServerClientService.uploadInstance(eq("http://dicom.local"), eq("u"), eq("p"), any(), eq(4L)))
                .thenAnswer(invocation -> {
                    int active = activeUploads.incrementAndGet();
                    maxActiveUploads.accumulateAndGet(active, Math::max);
                    bothUploadsStarted.countDown();
                    assertTrue(bothUploadsStarted.await(5, TimeUnit.SECONDS));
                    activeUploads.decrementAndGet();
                    return uploadResponse();
                });
        stubSuccessfulStudyPersistence();

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("one.dcm", new byte[] {1, 2, 3, 4});
        entries.put("two.dcm", new byte[] {5, 6, 7, 8});
        Path zipPath = tempDir.resolve("parallel.zip");
        Files.write(zipPath, zipBytes(entries));
        List<Integer> percents = new CopyOnWriteArrayList<>();
        List<String> stages = new CopyOnWriteArrayList<>();
        DicomUploadProgressListener listener = (percent, processed, total, stage) -> {
            percents.add(percent);
            stages.add(stage);
        };

        ResponseMessage<BaseResult> response = service.uploadDicomZipFile(
                null,
                null,
                zipPath,
                Files.size(zipPath),
                new MockHttpServletRequest(),
                listener
        );

        assertTrue(response.isSuccess());
        DicomUploadResponse body = (DicomUploadResponse) response.getBody().getData().get(0);
        assertEquals(2, body.getAcceptedFiles());
        assertTrue(maxActiveUploads.get() >= 2);
        assertTrue(percents.stream().anyMatch(percent -> percent > 10 && percent < 100));
        assertEquals(100, percents.getLast());
        assertTrue(stages.contains("Forwarding DICOM instances"));
        assertTrue(stages.contains("Synchronizing study metadata"));
    }

    private void stubSuccessfulStudyPersistence() {
        when(dicomServerClientService.getStudyById(eq("http://dicom.local"), eq("u"), eq("p"), eq("orthanc-study-1")))
                .thenReturn(studyResponse());
        when(dicomServerClientService.getSeriesByStudyId(eq("http://dicom.local"), eq("u"), eq("p"), eq("orthanc-study-1")))
                .thenReturn(List.of(seriesResponse()));
        when(modalityMapper.findActiveHospitalModalityByDicomCode(11L, "CT")).thenReturn(modality());
        when(patientMapper.findByDemographics(eq(11L), eq("HEL"), eq("SOK"), eq(LocalDate.of(1975, 1, 1)), eq("M")))
                .thenReturn(patient());
        when(studyMapper.upsertFromDicomUpload(eq(11L), eq(55L), eq("1.2.3.4"), eq("ACC-2023"), eq("ACC-2023"), eq(3L), eq("CT"),
                eq(LocalDate.of(2023, 9, 26)), eq("CT CHEST"), eq("TSNH HOSPITAL"), eq(5L), eq(1), eq("orthanc-study-1"), eq("orthanc-patient-1"),
                eq("series-1"), eq(3), eq(99L), any())).thenReturn(77L);
        when(studyMapper.findById(11L, 77L)).thenReturn(savedStudy());
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
                "StudyDescription", "CT CHEST",
                "InstitutionName", "TSNH HOSPITAL"
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
        response.setPatientHn("PID-HEL");
        response.setFirstName("HEL");
        response.setLastName("SOK");
        response.setGender("M");
        response.setDateOfBirth(LocalDate.of(1975, 1, 1));
        return response;
    }

    private static StudyResponse savedStudy() {
        StudyResponse response = new StudyResponse();
        response.setId(77L);
        response.setHospitalId(11L);
        response.setPublicKey("study-key");
        response.setPatientName("HEL SOK");
        response.setStudyInstanceUid("1.2.3.4");
        response.setStudyDescription("CT CHEST");
        response.setInstitutionName("TSNH HOSPITAL");
        response.setStatus("IMAGE_RECEIVED");
        return response;
    }

    private static byte[] zipBytes(String entryName, byte[] content) throws Exception {
        return zipBytes(Map.of(entryName, content));
    }

    private static byte[] zipBytes(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> item : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(item.getKey());
                zip.putNextEntry(entry);
                zip.write(item.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
