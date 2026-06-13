package com.ut.emrPacs.controller;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.systemSettings.hospital.HospitalRequestUpdate;
import com.ut.emrPacs.service.service.HospitalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HospitalControllerPayloadTest {

    private MockMvc mockMvc;
    private HospitalService hospitalService;

    @BeforeEach
    void setUp() throws Exception {
        hospitalService = mock(HospitalService.class);
        HospitalController controller = new HospitalController();
        ReflectionTestUtils.setField(controller, "hospitalService", hospitalService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        ResponseMessage<BaseResult> successResponse = ResponseMessageUtils.makeResponse(true, new BaseResult());
        when(hospitalService.createHospital(any(HospitalRequestUpdate.class), any())).thenReturn(successResponse);
        when(hospitalService.updateHospital(any(HospitalRequestUpdate.class), any())).thenReturn(successResponse);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "tester",
                        "N/A",
                        AuthorityUtils.createAuthorityList("ROLE_USER")
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createHospitalShouldAcceptNumericHospitalUserList() throws Exception {
        String requestBody = """
                {
                  "code":"HSP001",
                  "name":"City General Hospital",
                  "hospitalUserList":[101,102],
                  "modalityIds":[10,11]
                }
                """;

        mockMvc.perform(post("/hospital/hospital-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        ArgumentCaptor<HospitalRequestUpdate> captor = ArgumentCaptor.forClass(HospitalRequestUpdate.class);
        verify(hospitalService).createHospital(captor.capture(), any());
        assertEquals(List.of(101L, 102L), captor.getValue().getHospitalUserList());
    }

    @Test
    void updateHospitalShouldAcceptLegacyHospitalUserListObjectFormat() throws Exception {
        String requestBody = """
                {
                  "id":1,
                  "code":"HSP001",
                  "name":"City General Hospital",
                  "hospitalUserList":[{"userId":201},{"userId":202}],
                  "modalityIds":[21,22]
                }
                """;

        mockMvc.perform(post("/hospital/hospital-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        ArgumentCaptor<HospitalRequestUpdate> captor = ArgumentCaptor.forClass(HospitalRequestUpdate.class);
        verify(hospitalService).updateHospital(captor.capture(), any());
        assertEquals(List.of(201L, 202L), captor.getValue().getHospitalUserList());
    }
}
