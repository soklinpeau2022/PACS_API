package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.mapper.modality.ModalityMapper;
import com.ut.emrPacs.mapper.pacs.PatientMapper;
import com.ut.emrPacs.mapper.pacs.WorklistMapper;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistSendToPacsRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalModalityServerRouteResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistCreateResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistMachineRouteChoiceResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import com.ut.emrPacs.model.enums.WorklistStatus;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DicomServerClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class WorklistServiceImplFlowStatusTest {

    @Mock
    private WorklistMapper WorklistMapper;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private ModalityMapper modalityMapper;
    @Mock
    private DicomServerMapper dicomServerMapper;
    @Mock
    private DicomServerClientService dicomServerClientService;
    @Mock
    private PublicEntityKeyResolver publicEntityKeyResolver;

    private WorklistServiceImpl WorklistService;

    @BeforeEach
    void setUp() {
        WorklistService = new WorklistServiceImpl();
        ReflectionTestUtils.setField(WorklistService, "WorklistMapper", WorklistMapper);
        ReflectionTestUtils.setField(WorklistService, "messageService", new MessageService());
        ReflectionTestUtils.setField(WorklistService, "activityLogService", activityLogService);
        ReflectionTestUtils.setField(WorklistService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(WorklistService, "modalityMapper", modalityMapper);
        ReflectionTestUtils.setField(WorklistService, "dicomServerMapper", dicomServerMapper);
        ReflectionTestUtils.setField(WorklistService, "dicomServerClientService", dicomServerClientService);
        ReflectionTestUtils.setField(WorklistService, "publicEntityKeyResolver", publicEntityKeyResolver);
        lenient().when(publicEntityKeyResolver.resolve(any(PublicEntityKeyResolver.Entity.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "n/a", "ROLE_ADMIN");
        auth.setAuthenticated(true);
        auth.setDetails(new CurrentUserPrincipal(99L, "admin", 11L, "HSP001", "pacs-web", "jti-1", 1L));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendToPacsShouldMoveWaitingToInProgress() throws Exception {
        WorklistDetailRow Worklist = new WorklistDetailRow();
        Worklist.setId(1201L);
        Worklist.setHospitalId(11L);
        Worklist.setPatientId(501L);
        Worklist.setStatus(WorklistStatus.WAITING.name());
        Worklist.setVisitCode("CT-KSFH-260526-0001");
        Worklist.setPatientUid("PT-001");
        Worklist.setPatientName("John");
        Worklist.setModalityId(5L);

        when(WorklistMapper.findWorklistById(anyLong(), eq(1201L))).thenReturn(Worklist);
        when(dicomServerMapper.listActiveRoutesByHospitalAndModality(11L, 5L)).thenReturn(List.of(localRoute()));
        when(dicomServerMapper.getDicomServerById(4L, 11L)).thenReturn(List.of(localDicomServer()));
        DicomServerWorklistCreateResponse worklistResponse = new DicomServerWorklistCreateResponse();
        worklistResponse.setId("wl-1");
        worklistResponse.setPath("/worklists/wl-1");
        when(dicomServerClientService.postToDicomServerWorklist(eq("http://localhost:8042"), eq("dicom_server"), eq("dicom_server"), any())).thenReturn(worklistResponse);
        when(WorklistMapper.updateWorklistSentToPacsById(anyLong(), eq(1201L), eq(WorklistStatus.IN_PROGRESS.code()), eq(4L), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        WorklistSendToPacsRequest request = new WorklistSendToPacsRequest();
        request.setWorklistId(1201L);

        ResponseMessage<BaseResult> response = WorklistService.sendToPacs(request, null);

        assertTrue(response.isSuccess(), response.getHeader() != null ? String.valueOf(response.getHeader().getErrorText()) : "Unknown error");
        verify(WorklistMapper).updateWorklistSentToPacsById(anyLong(), eq(1201L), eq(WorklistStatus.IN_PROGRESS.code()), eq(4L), eq(14L), eq("CT-KSFH-260526-0001"), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendToPacsShouldMarkWorklistFailedWhenDicomServerIsUnreachable() throws Exception {
        WorklistDetailRow Worklist = new WorklistDetailRow();
        Worklist.setId(1203L);
        Worklist.setHospitalId(11L);
        Worklist.setPatientId(501L);
        Worklist.setStatus(WorklistStatus.WAITING.name());
        Worklist.setVisitCode("OT-KSFH-260516-0004");
        Worklist.setPatientUid("PT-003");
        Worklist.setPatientName("John");
        Worklist.setModalityId(5L);

        when(WorklistMapper.findWorklistById(11L, 1203L)).thenReturn(Worklist);
        when(dicomServerMapper.listActiveRoutesByHospitalAndModality(11L, 5L)).thenReturn(List.of(localRoute()));
        when(dicomServerMapper.getDicomServerById(4L, 11L)).thenReturn(List.of(localDicomServer()));
        when(dicomServerClientService.postToDicomServerWorklist(eq("http://localhost:8042"), eq("dicom_server"), eq("dicom_server"), any())).thenThrow(new ResourceAccessException("DicomServer down"));
        when(WorklistMapper.updateWorklistWorkflowStatusById(11L, 1203L, WorklistStatus.FAILED.code(), "DicomServer is unreachable.", 99L)).thenReturn(1);

        WorklistSendToPacsRequest request = new WorklistSendToPacsRequest();
        request.setWorklistId(1203L);

        ResponseMessage<BaseResult> response = WorklistService.sendToPacs(request, null);

        assertFalse(response.isSuccess());
        verify(WorklistMapper).updateWorklistWorkflowStatusById(11L, 1203L, WorklistStatus.FAILED.code(), "DicomServer is unreachable.", 99L);
        verify(WorklistMapper).insertHistory(
                11L,
                1203L,
                501L,
                WorklistStatus.WAITING.code(),
                WorklistStatus.FAILED.code(),
                "Send Worklist",
                "DicomServer is unreachable.",
                99L
        );
    }

    @Test
    void machineRoutesShouldLoadByHospitalAndModalityBeforeWorklistExists() throws Exception {
        WorklistSendToPacsRequest request = new WorklistSendToPacsRequest();
        request.setHospitalKey("hospital-11");
        request.setModalityKey("modality-5");

        WorklistMachineRouteChoiceResponse route = new WorklistMachineRouteChoiceResponse();
        route.setId(14L);
        route.setMachineName("CT Room 1");
        when(publicEntityKeyResolver.resolve(PublicEntityKeyResolver.Entity.HOSPITAL, "hospital-11", null)).thenReturn(11L);
        when(publicEntityKeyResolver.resolve(PublicEntityKeyResolver.Entity.MODALITY, "modality-5", null)).thenReturn(5L);
        when(modalityMapper.countActiveModalitiesByIds(List.of(5L))).thenReturn(1L);
        when(dicomServerMapper.listActiveRouteChoicesByHospitalAndModality(11L, 5L)).thenReturn(List.of(route));

        ResponseMessage<BaseResult> response = WorklistService.listMachineRoutesForSend(request, null);

        assertTrue(response.isSuccess(), response.getHeader() != null ? String.valueOf(response.getHeader().getErrorText()) : "Unknown error");
        verify(dicomServerMapper).listActiveRouteChoicesByHospitalAndModality(11L, 5L);
    }

    @Test
    void machineRoutesShouldRequireWorklistVisitCodeOrModality() throws Exception {
        ResponseMessage<BaseResult> response = WorklistService.listMachineRoutesForSend(new WorklistSendToPacsRequest(), null);

        assertFalse(response.isSuccess());
    }

    private HospitalDicomServerResponse localDicomServer() {
        HospitalDicomServerResponse server = new HospitalDicomServerResponse();
        server.setId(4L);
        server.setHospitalId(11L);
        server.setBaseUrl("http://localhost:8042");
        server.setDicomServerUiBaseUrl("http://localhost:8042");
        server.setUsername("dicom_server");
        server.setPassword("dicom_server");
        server.setAeTitle("dicom_server");
        return server;
    }

    private HospitalModalityServerRouteResponse localRoute() {
        HospitalModalityServerRouteResponse route = new HospitalModalityServerRouteResponse();
        route.setId(14L);
        route.setHospitalId(11L);
        route.setModalityId(5L);
        route.setDicomServerId(4L);
        route.setMachineName("CT Room 1");
        route.setMachineAeTitle("UDAYA");
        route.setMachineHost("192.168.1.50");
        route.setMachinePort(4242);
        return route;
    }

}
