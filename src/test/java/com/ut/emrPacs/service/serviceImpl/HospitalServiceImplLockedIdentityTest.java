package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.hospital.HospitalMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.components.systemSettings.hospital.Hospital;
import com.ut.emrPacs.model.dto.request.systemSettings.hospital.HospitalRequestUpdate;
import com.ut.emrPacs.model.dto.response.systemSettings.hospital.HospitalResponseDetail;
import com.ut.emrPacs.service.service.ActivityLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HospitalServiceImplLockedIdentityTest {

    @Mock
    private HospitalMapper hospitalMapper;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @Test
    void updateHospitalShouldRejectIdentityChangesAfterDicomPackageBuild() throws Exception {
        HospitalServiceImpl service = new HospitalServiceImpl();
        ReflectionTestUtils.setField(service, "messageService", new MessageService());
        ReflectionTestUtils.setField(service, "hospitalMapper", hospitalMapper);
        ReflectionTestUtils.setField(service, "activityLogService", activityLogService);
        ReflectionTestUtils.setField(service, "publicEntityKeyResolver", publicEntityKeyResolver);

        HospitalResponseDetail existing = new HospitalResponseDetail();
        existing.setCode("H001");
        existing.setAbbr("KSFH");
        existing.setHospitalName("KSFH Hospital");
        existing.setTimezone("Asia/Phnom_Penh");
        existing.setDeploymentLocked(true);

        HospitalRequestUpdate request = new HospitalRequestUpdate();
        request.setId(1L);
        request.setCode("H999");
        request.setAbbr("KSFH");
        request.setName("KSFH Hospital");
        request.setTimezone("Asia/Phnom_Penh");

        when(publicEntityKeyResolver.resolve(eq(Entity.HOSPITAL), isNull(), eq(1L))).thenReturn(1L);
        when(hospitalMapper.getHospitalById(1L)).thenReturn(List.of(existing));

        ResponseMessage<BaseResult> response = service.updateHospital(request, new MockHttpServletRequest());

        assertFalse(response.isSuccess());
        verify(hospitalMapper, never()).updateHospital(any(Hospital.class));
        verify(hospitalMapper, never()).countHospitalCode(any(), any());
    }
}
