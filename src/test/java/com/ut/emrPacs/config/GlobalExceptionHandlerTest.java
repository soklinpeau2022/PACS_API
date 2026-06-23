package com.ut.emrPacs.config;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.service.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldReturnNotFoundForNoResourceFoundException() {
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.POST, "/user/update", "/user/update");

        ResponseEntity<ResponseMessage<BaseResult>> response = handler.handleNotFound(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getHeader().getStatusCode());
    }

    @Test
    void shouldReturnNotFoundForNoHandlerFoundException() {
        NoHandlerFoundException exception = new NoHandlerFoundException("POST", "/role/role-list", new HttpHeaders());

        ResponseEntity<ResponseMessage<BaseResult>> response = handler.handleNotFound(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getHeader().getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForMissingMultipartPart() {
        MissingServletRequestPartException exception = new MissingServletRequestPartException("images");

        ResponseEntity<ResponseMessage<BaseResult>> response = handler.handleInvalidMultipart(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getHeader().getStatusCode());
    }

    @Test
    void shouldReturnPayloadTooLargeForMultipartLimit() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(4294967296L);

        ResponseEntity<ResponseMessage<BaseResult>> response = handler.handleMaxUploadSize(exception);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(413, response.getBody().getHeader().getStatusCode());
    }

    @Test
    void shouldReturnFriendlyConstraintViolationMessage() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<RetentionRequest>> violations =
                validator.validate(new RetentionRequest(3651));

        ResponseEntity<ResponseMessage<BaseResult>> response =
                handler.handleValidation(new ConstraintViolationException(violations));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                "Retention period cannot exceed 3650 days.",
                response.getBody().getBody().getMessage()
        );
        assertEquals(
                "Retention period cannot exceed 3650 days.",
                response.getBody().getHeader().getErrorText()
        );
    }

    @Test
    void shouldRecordUnexpectedServerErrorsToActivityLog() throws Exception {
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        GlobalExceptionHandler handlerWithAudit = new GlobalExceptionHandler(activityLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/worklist/worklist-list");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ResponseEntity<ResponseMessage<BaseResult>> response = handlerWithAudit.handleUnexpected(new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        verify(activityLogService).insert(
                eq("/pacsApi/worklist/worklist-list"),
                any(Long.class),
                contains("boom"),
                eq("Global Exception"),
                eq("Global Exception (Unhandled)"),
                eq("UNHANDLED_EXCEPTION"),
                eq(2),
                eq("Unhandled Exception"),
                any(LocalTime.class),
                any(LocalTime.class),
                same(request)
        );
    }

    @Test
    void shouldNotRecordGlobalErrorTwiceWhenServiceAlreadyLoggedIt() throws Exception {
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        GlobalExceptionHandler handlerWithAudit = new GlobalExceptionHandler(activityLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/patient/patient-list");
        ErrorReportingAttributes.markErrorActivityLogged(request);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        handlerWithAudit.handleUnexpected(new RuntimeException("boom"));

        verify(activityLogService, never()).insert(
                any(String.class),
                any(Long.class),
                any(String.class),
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
    void shouldNotRecordNotificationStreamTimeoutAsGlobalError() throws Exception {
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        GlobalExceptionHandler handlerWithAudit = new GlobalExceptionHandler(activityLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/pacsApi/notification/notification-stream");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        handlerWithAudit.handleAsyncRequestTimeout(new AsyncRequestTimeoutException());

        verify(activityLogService, never()).insert(
                any(String.class),
                any(Long.class),
                any(String.class),
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
    void shouldReturnRetryableServiceUnavailableWhenStreamingCapacityIsFull() {
        ResponseEntity<ResponseMessage<BaseResult>> response = handler.handleAsyncCapacityExceeded(
                new TaskRejectedException("stream pool full")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(503, response.getBody().getHeader().getStatusCode());
        assertEquals(
                "The imaging service is busy. Please retry shortly.",
                response.getBody().getBody().getMessage()
        );
    }

    private record RetentionRequest(
            @Max(value = 3650, message = "Retention period cannot exceed 3650 days.")
            int retentionDays
    ) {
    }
}
