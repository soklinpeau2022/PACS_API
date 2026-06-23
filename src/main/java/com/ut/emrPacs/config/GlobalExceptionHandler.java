package com.ut.emrPacs.config;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.service.service.ActivityLogService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalTime;
import java.util.Comparator;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final int LOG_STATUS_ERROR = 2;

    private static final String MESSAGE_BAD_REQUEST = "Invalid request parameters.";
    private static final String MESSAGE_MALFORMED_JSON = "Malformed JSON request.";
    private static final String MESSAGE_FORBIDDEN = "Forbidden.";
    private static final String MESSAGE_UNAUTHORIZED = "Unauthorized.";
    private static final String MESSAGE_METHOD_NOT_ALLOWED = "Method not allowed.";
    private static final String MESSAGE_UNSUPPORTED_MEDIA = "Unsupported media type.";
    private static final String MESSAGE_NOT_FOUND = "Not Found.";
    private static final String MESSAGE_CONFLICT = "Request could not be processed.";
    private static final String MESSAGE_INTERNAL_ERROR = "An unexpected error occurred. Please try again.";

    private final ActivityLogService activityLogService;

    public GlobalExceptionHandler() {
        this(null);
    }

    @Autowired
    public GlobalExceptionHandler(@Lazy ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ResponseMessage<BaseResult>> handleBadRequest(Exception exception) {
        LOGGER.warn("Bad request: {}", exception.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", MESSAGE_BAD_REQUEST);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public ResponseEntity<ResponseMessage<BaseResult>> handleValidation(Exception exception) {
        LOGGER.warn("Validation error: {}", exception.getMessage());
        String clientMessage = resolveValidationMessage(exception);
        ResponseMessage<BaseResult> response;
        if (exception instanceof MethodArgumentNotValidException ex) {
            response = ResponseMessageUtils.makeResponse(false, ex.getBindingResult());
        } else if (exception instanceof BindException ex) {
            response = ResponseMessageUtils.makeResponse(false, ex.getBindingResult());
        } else {
            response = ResponseMessageUtils.makeResponse(false, HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", clientMessage);
        }
        attachBody(response, clientMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseMessage<BaseResult>> handleMalformedJson(HttpMessageNotReadableException exception) {
        LOGGER.warn("Malformed JSON: {}", exception.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", MESSAGE_MALFORMED_JSON);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ResponseMessage<BaseResult>> handleInvalidMultipart(Exception exception) {
        Throwable cause = exception.getCause();
        LOGGER.warn(
                "Invalid multipart request: {}; cause={}",
                exception.getMessage(),
                cause == null ? "n/a" : cause.toString()
        );
        return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", MESSAGE_BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ResponseMessage<BaseResult>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        LOGGER.warn("Multipart upload too large: {}", exception.getMessage());
        return buildError(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request payload too large.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseMessage<BaseResult>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        LOGGER.warn("Method not allowed: {}", exception.getMessage());
        return buildError(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", MESSAGE_METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ResponseMessage<BaseResult>> handleUnsupportedMedia(HttpMediaTypeNotSupportedException exception) {
        LOGGER.warn("Unsupported media type: {}", exception.getMessage());
        return buildError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", MESSAGE_UNSUPPORTED_MEDIA);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ResponseMessage<BaseResult>> handleNotFound(Exception exception) {
        LOGGER.warn("Not found: {}", exception.getMessage());
        return buildError(HttpStatus.NOT_FOUND, "NOT_FOUND", MESSAGE_NOT_FOUND);
    }

    @ExceptionHandler({AccessDeniedException.class})
    public ResponseEntity<ResponseMessage<BaseResult>> handleForbidden(AccessDeniedException exception) {
        LOGGER.warn("Access denied: {}", exception.getMessage());
        return buildError(HttpStatus.FORBIDDEN, "FORBIDDEN", MESSAGE_FORBIDDEN);
    }

    @ExceptionHandler({AuthenticationException.class})
    public ResponseEntity<ResponseMessage<BaseResult>> handleUnauthorized(AuthenticationException exception) {
        LOGGER.warn("Unauthorized: {}", exception.getMessage());
        return buildError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", MESSAGE_UNAUTHORIZED);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, PersistenceException.class})
    public ResponseEntity<ResponseMessage<BaseResult>> handleDataErrors(Exception exception) {
        LOGGER.error("Data access error", exception);
        recordUnhandledError(exception, "Global Exception", "Global Exception (Data Access)", "DATA_ACCESS_ERROR", "Data Access Error");
        return buildError(HttpStatus.CONFLICT, "CONFLICT", MESSAGE_CONFLICT);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(AsyncRequestNotUsableException exception) {
        // The client (browser/viewer) closed the connection before the response finished streaming.
        // This is normal when the viewer cancels in-flight frame prefetches; the response can no
        // longer be written, so there is nothing to return. Log at DEBUG to avoid flooding ERROR.
        LOGGER.debug("Client disconnected before the response completed: {}", exception.getMessage());
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeout(AsyncRequestTimeoutException exception) {
        // Long-lived SSE requests such as /notification/notification-stream naturally expire and
        // reconnect. Treat that as connection lifecycle noise, not an application failure.
        LOGGER.debug("Async request timed out before completion: {}", exception.getMessage());
    }

    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<ResponseMessage<BaseResult>> handleAsyncCapacityExceeded(TaskRejectedException exception) {
        LOGGER.warn("Async streaming capacity temporarily exhausted: {}", exception.getMessage());
        return buildError(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SERVICE_UNAVAILABLE",
                "The imaging service is busy. Please retry shortly."
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseMessage<BaseResult>> handleUnexpected(Exception exception) {
        LOGGER.error("Unhandled exception", exception);
        recordUnhandledError(exception, "Global Exception", "Global Exception (Unhandled)", "UNHANDLED_EXCEPTION", "Unhandled Exception");
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", MESSAGE_INTERNAL_ERROR);
    }

    private void recordUnhandledError(Exception exception, String moduleName, String moduleId, String action, String description) {
        if (activityLogService == null) {
            return;
        }
        HttpServletRequest request = resolveHttpRequest();
        if (ErrorReportingAttributes.isErrorActivityLogged(request)) {
            return;
        }

        LocalTime now = LocalTime.now();
        try {
            activityLogService.insert(
                    resolveEndpoint(request),
                    resolveErrorLine(exception),
                    exception.toString(),
                    moduleName,
                    moduleId,
                    action,
                    LOG_STATUS_ERROR,
                    description,
                    now,
                    now,
                    request
            );
        } catch (Exception auditError) {
            LOGGER.warn("Global exception audit insert failed: {}", auditError.getMessage());
        }
    }

    private static String resolveEndpoint(HttpServletRequest request) {
        if (request != null && request.getRequestURI() != null && !request.getRequestURI().isBlank()) {
            return request.getRequestURI();
        }
        return "/internal/global-exception";
    }

    private static Long resolveErrorLine(Throwable error) {
        if (error == null || error.getStackTrace() == null || error.getStackTrace().length == 0) {
            return null;
        }
        return (long) error.getStackTrace()[0].getLineNumber();
    }

    private static HttpServletRequest resolveHttpRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private static ResponseEntity<ResponseMessage<BaseResult>> buildError(HttpStatus status, String code, String message) {
        ResponseMessage<BaseResult> response = ResponseMessageUtils.makeResponse(false, status.value(), code, message);
        attachBody(response, message);
        return ResponseEntity.status(status).body(response);
    }

    private static String resolveValidationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException ex) {
            return ex.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst()
                    .orElse(MESSAGE_BAD_REQUEST);
        }
        if (exception instanceof BindException ex) {
            return ex.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst()
                    .orElse(MESSAGE_BAD_REQUEST);
        }
        if (exception instanceof ConstraintViolationException ex) {
            return ex.getConstraintViolations().stream()
                    .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                    .map(ConstraintViolation::getMessage)
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst()
                    .orElse(MESSAGE_BAD_REQUEST);
        }
        return MESSAGE_BAD_REQUEST;
    }

    private static void attachBody(ResponseMessage<BaseResult> response, String message) {
        BaseResult body = new BaseResult();
        body.setStatus(false);
        body.setMessage(message);
        response.setBody(body);
    }
}
