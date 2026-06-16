package com.ut.emrPacs.controller;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultSaveRequest;
import com.ut.emrPacs.service.service.PacsResultService;
import com.ut.emrPacs.service.service.WorklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PacsResultApiControllerPayloadTest {

    private MockMvc mockMvc;
    private PacsResultService pacsResultService;

    @BeforeEach
    void setUp() throws Exception {
        pacsResultService = mock(PacsResultService.class);
        WorklistService worklistService = mock(WorklistService.class);
        PacsResultApiController controller = new PacsResultApiController(pacsResultService, worklistService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        ResponseMessage<BaseResult> successResponse = ResponseMessageUtils.makeResponse(true, new BaseResult());
        when(pacsResultService.createBrowser(any(PacsResultSaveRequest.class), eq(List.of()), any()))
                .thenReturn(successResponse);
    }

    @Test
    void createResultShouldAcceptJsonPayloadWithoutImages() throws Exception {
        String requestBody = """
                {
                  "resultDate": "2026-06-16",
                  "resultText": "<p>IRM PELVIEN</p>",
                  "completed": true,
                  "hospitalKey": "2b8f3be9-49a3-a2ff-f089-59a3f6b123f1",
                  "modalityKey": "2576b2c9-b364-ad64-642e-3e2e96324a69",
                  "studyKey": "a8fd8e5f-b3ac-18ce-435b-3716c47c09a4",
                  "studyInstanceUid": "1.2.392.200036.9116.2.6.1.31760.3384969567.1695719228.12243",
                  "accessionNumber": "123",
                  "patientKey": "490123a5-ff9d-a1c5-d85c-e001e686e626",
                  "patientCode": "26-H001-P0000003",
                  "patientName": "HEL SOK",
                  "templateKey": "1a799c65-498f-dbf8-99b9-f600cfe96416"
                }
                """;

        mockMvc.perform(post("/pacs-result-api/pacs-result-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        ArgumentCaptor<PacsResultSaveRequest> captor = ArgumentCaptor.forClass(PacsResultSaveRequest.class);
        verify(pacsResultService).createBrowser(captor.capture(), eq(List.of()), any());
        assertEquals("123", captor.getValue().getAccessionNumber());
        assertTrue(captor.getValue().getCompleted());
    }
}
