package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.helper.FunctionCodeGenerate;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.mapper.hospital.HospitalMapper;
import com.ut.emrPacs.mapper.pacs.PatientMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.pacs.patient.PatientCreateRequest;
import com.ut.emrPacs.model.dto.response.pacs.patient.PatientResponse;
import com.ut.emrPacs.model.dto.response.systemSettings.hospital.HospitalResponseDetail;
import com.ut.emrPacs.service.service.ActivityLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceImplCreateTest {

    @Mock
    private PatientMapper patientMapper;
    @Mock
    private HospitalMapper hospitalMapper;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private PublicEntityKeyResolver publicEntityKeyResolver;

    private PatientServiceImpl patientService;

    @BeforeEach
    void setUp() {
        patientService = new PatientServiceImpl();
        ReflectionTestUtils.setField(patientService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(patientService, "hospitalMapper", hospitalMapper);
        ReflectionTestUtils.setField(patientService, "messageService", new MessageService());
        ReflectionTestUtils.setField(patientService, "activityLogService", activityLogService);
        ReflectionTestUtils.setField(patientService, "publicEntityKeyResolver", publicEntityKeyResolver);

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
    void createShouldReturnCreatedPatientForWorklistHandoff() throws Exception {
        String patientCode = FunctionCodeGenerate.buildPatientCode(
                FunctionCodeGenerate.currentPatientYearPrefix(),
                "KSFH",
                7L
        );

        HospitalResponseDetail hospital = new HospitalResponseDetail();
        hospital.setAbbr("KSFH");
        hospital.setCode("H001");
        hospital.setHospitalName("KSFH Hospital");
        when(hospitalMapper.getHospitalById(11L)).thenReturn(List.of(hospital));
        when(patientMapper.existsPatientSequenceTable()).thenReturn(true);
        when(patientMapper.nextPatientSequenceByYear(11L, FunctionCodeGenerate.currentPatientYearPrefix(), "KSFH"))
                .thenReturn(7L);
        when(patientMapper.create(eq(11L), any(PatientCreateRequest.class))).thenReturn(501L);

        PatientResponse createdPatient = new PatientResponse();
        createdPatient.setId(501L);
        createdPatient.setPublicKey("patient-public-key");
        createdPatient.setHospitalId(11L);
        createdPatient.setPatientCode(patientCode);
        createdPatient.setFirstName("Soklin");
        createdPatient.setLastName("");
        createdPatient.setPhoneNumber("012345678");
        createdPatient.setGender("F");
        createdPatient.setDateOfBirth(LocalDate.of(1900, 1, 1));
        when(patientMapper.findById(11L, 501L)).thenReturn(createdPatient);

        PatientCreateRequest request = new PatientCreateRequest();
        request.setFirstName(" Soklin ");
        request.setLastName(" ");
        request.setPhoneNumber(" 012345678 ");
        request.setGender(" F ");

        ResponseMessage<BaseResult> response = patientService.create(request, null);

        assertTrue(response.isSuccess(), response.getHeader() != null ? String.valueOf(response.getHeader().getErrorText()) : "Unknown error");
        assertEquals(List.of(createdPatient), response.getBody().getData());
        assertSame(createdPatient, response.getBody().getData().get(0));
        assertEquals("Soklin", request.getFirstName());
        assertEquals("", request.getLastName());
        assertEquals("012345678", request.getPhoneNumber());
        assertEquals("F", request.getGender());
        assertEquals(LocalDate.of(1900, 1, 1), request.getDateOfBirth());
        assertEquals(patientCode, request.getPatientCode());
        verify(patientMapper).create(eq(11L), eq(request));
        verify(patientMapper).findById(11L, 501L);
    }
}
