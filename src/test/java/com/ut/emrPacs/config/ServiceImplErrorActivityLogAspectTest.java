package com.ut.emrPacs.config;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.service.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceImplErrorActivityLogAspectTest {

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldNotLogExpectedFailedServiceResponsesAsSystemErrors() throws Throwable {
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        ServiceImplErrorActivityLogAspect aspect = new ServiceImplErrorActivityLogAspect(activityLogService);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        ResponseMessage<BaseResult> response = ResponseMessageUtils.makeResponse(false, 401, "UNAUTHORIZED", "Unauthorized");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/auth/auth-refresh");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(joinPoint.proceed()).thenReturn(response);

        Object result = aspect.logUnhandledServiceErrors(joinPoint);

        assertSame(response, result);
        verify(activityLogService, never()).insert(
                any(String.class),
                any(),
                any(),
                any(String.class),
                any(String.class),
                any(String.class),
                eq(2),
                any(String.class),
                any(LocalTime.class),
                any(LocalTime.class),
                any(HttpServletRequest.class)
        );
    }

    @Test
    void shouldLogThrownServiceExceptionsAsSystemErrors() throws Throwable {
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        ServiceImplErrorActivityLogAspect aspect = new ServiceImplErrorActivityLogAspect(activityLogService);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        RuntimeException error = new RuntimeException("boom");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/worklist/worklist-list");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.proceed()).thenThrow(error);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn((Class) com.ut.emrPacs.service.serviceImpl.WorklistServiceImpl.class);
        when(signature.getName()).thenReturn("list");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> aspect.logUnhandledServiceErrors(joinPoint));

        assertSame(error, thrown);
        verify(activityLogService).insert(
                eq("/pacsApi/worklist/worklist-list"),
                any(),
                eq(error.toString()),
                eq("Worklist"),
                eq("Worklist (list)"),
                eq("list"),
                eq(2),
                eq("Exception"),
                any(LocalTime.class),
                any(LocalTime.class),
                same(request)
        );
    }

    @Test
    void shouldNotLogDicomServerBadRequestUploadRejectionsAsSystemErrors() throws Throwable {
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        ServiceImplErrorActivityLogAspect aspect = new ServiceImplErrorActivityLogAspect(activityLogService);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        IllegalStateException error = new IllegalStateException("DICOM server upload failed with HTTP 400.");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/dicom-uploads/chunk/upload-1/complete");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.proceed()).thenThrow(error);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> aspect.logUnhandledServiceErrors(joinPoint));

        assertSame(error, thrown);
        verify(activityLogService, never()).insert(
                any(String.class),
                any(),
                any(),
                any(String.class),
                any(String.class),
                any(String.class),
                eq(2),
                any(String.class),
                any(LocalTime.class),
                any(LocalTime.class),
                any(HttpServletRequest.class)
        );
    }
}
