package com.ut.emrPacs.controller;

import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistActionRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistDicomWorklistUpdateRequest;
import com.ut.emrPacs.service.service.WorklistService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorklistRestControllerTest {

    private static final String WORKLIST_KEY = "5fdc7404-f9dc-4798-b6e1-8f715e2f9e71";
    private static final Long WORKLIST_ID = 42L;

    private MockMvc mockMvc;
    private WorklistService worklistService;
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @BeforeEach
    void setUp() throws Exception {
        worklistService = mock(WorklistService.class);
        publicEntityKeyResolver = mock(PublicEntityKeyResolver.class);

        WorklistRestController controller = new WorklistRestController();
        ReflectionTestUtils.setField(controller, "worklistService", worklistService);
        ReflectionTestUtils.setField(controller, "publicEntityKeyResolver", publicEntityKeyResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(publicEntityKeyResolver.resolveFromPath(Entity.WORKLIST, WORKLIST_KEY, "Worklist"))
                .thenReturn(WORKLIST_ID);
        when(worklistService.getWorklist(eq(WORKLIST_ID), any(HttpServletRequest.class)))
                .thenReturn(okResponse());
        when(worklistService.updateWorklist(eq(WORKLIST_ID), any(WorklistDicomWorklistUpdateRequest.class), any(HttpServletRequest.class)))
                .thenReturn(okResponse());
        when(worklistService.deleteWorklist(eq(WORKLIST_ID), any(HttpServletRequest.class)))
                .thenReturn(okResponse());
        when(worklistService.updateStatus(any(WorklistActionRequest.class), eq("CANCELLED"), any(HttpServletRequest.class)))
                .thenReturn(okResponse());
    }

    @Test
    void getWorklistShouldResolvePathKeyAndDelegateToService() throws Exception {
        mockMvc.perform(get("/worklists/{worklistId}", WORKLIST_KEY))
                .andExpect(status().isOk());

        verify(publicEntityKeyResolver).resolveFromPath(Entity.WORKLIST, WORKLIST_KEY, "Worklist");
        verify(worklistService).getWorklist(eq(WORKLIST_ID), any(HttpServletRequest.class));
    }

    @Test
    void updateWorklistShouldResolvePathKeyAndForwardParsedBody() throws Exception {
        String requestBody = """
                {
                  "hospitalPublicKey": "hospital-key",
                  "modalityPublicKey": "modality-key",
                  "dicomServerPublicKey": "dicom-server-key",
                  "studyDescription": "Updated PACS Study",
                  "notes": "edit from ui"
                }
                """;

        mockMvc.perform(put("/worklists/{worklistId}", WORKLIST_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        ArgumentCaptor<WorklistDicomWorklistUpdateRequest> captor =
                ArgumentCaptor.forClass(WorklistDicomWorklistUpdateRequest.class);
        verify(publicEntityKeyResolver).resolveFromPath(Entity.WORKLIST, WORKLIST_KEY, "Worklist");
        verify(worklistService).updateWorklist(eq(WORKLIST_ID), captor.capture(), any(HttpServletRequest.class));
        assertEquals("hospital-key", captor.getValue().getHospitalKey());
        assertEquals("modality-key", captor.getValue().getModalityKey());
        assertEquals("dicom-server-key", captor.getValue().getDicomServerKey());
        assertEquals("Updated PACS Study", captor.getValue().getStudyDescription());
        assertEquals("edit from ui", captor.getValue().getNotes());
    }

    @Test
    void deleteWorklistShouldResolvePathKeyAndDelegateToService() throws Exception {
        mockMvc.perform(delete("/worklists/{worklistId}", WORKLIST_KEY))
                .andExpect(status().isOk());

        verify(publicEntityKeyResolver).resolveFromPath(Entity.WORKLIST, WORKLIST_KEY, "Worklist");
        verify(worklistService).deleteWorklist(eq(WORKLIST_ID), any(HttpServletRequest.class));
    }

    @Test
    void cancelWorklistShouldUsePathKeyAsActionIdAndKeepBodyNotes() throws Exception {
        String requestBody = """
                {
                  "hospitalPublicKey": "hospital-key",
                  "notes": "cancel from ui"
                }
                """;

        mockMvc.perform(post("/worklists/{worklistId}/cancel", WORKLIST_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        ArgumentCaptor<WorklistActionRequest> captor = ArgumentCaptor.forClass(WorklistActionRequest.class);
        verify(publicEntityKeyResolver).resolveFromPath(Entity.WORKLIST, WORKLIST_KEY, "Worklist");
        verify(worklistService).updateStatus(captor.capture(), eq("CANCELLED"), any(HttpServletRequest.class));
        assertEquals(WORKLIST_ID, captor.getValue().getId());
        assertEquals("hospital-key", captor.getValue().getHospitalKey());
        assertEquals("cancel from ui", captor.getValue().getNotes());
        assertNull(captor.getValue().getPublicKey());
    }

    @Test
    void cancelWorklistShouldAcceptMissingBodyAndUsePathKeyAsActionId() throws Exception {
        mockMvc.perform(post("/worklists/{worklistId}/cancel", WORKLIST_KEY))
                .andExpect(status().isOk());

        ArgumentCaptor<WorklistActionRequest> captor = ArgumentCaptor.forClass(WorklistActionRequest.class);
        verify(publicEntityKeyResolver).resolveFromPath(Entity.WORKLIST, WORKLIST_KEY, "Worklist");
        verify(worklistService).updateStatus(captor.capture(), eq("CANCELLED"), any(HttpServletRequest.class));
        assertEquals(WORKLIST_ID, captor.getValue().getId());
        assertNull(captor.getValue().getHospitalKey());
        assertNull(captor.getValue().getNotes());
        assertNull(captor.getValue().getPublicKey());
    }

    private static ResponseMessage<BaseResult> okResponse() {
        return ResponseMessageUtils.makeResponse(true, new BaseResult());
    }
}
