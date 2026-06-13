package com.ut.emrPacs.config;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import jakarta.validation.ConstraintViolationException;
import org.apache.ibatis.exceptions.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String MESSAGE_BAD_REQUEST = "Invalid request parameters.";
    private static final String MESSAGE_MALFORMED_JSON = "Malformed JSON request.";
    private static final String MESSAGE_FORBIDDEN = "Forbidden.";
    private static final String MESSAGE_UNAUTHORIZED = "Unauthorized.";
    private static final String MESSAGE_METHOD_NOT_ALLOWED = "Method not allowed.";
    private static final String MESSAGE_UNSUPPORTED_MEDIA = "Unsupported media type.";
    private static final String MESSAGE_NOT_FOUND = "Not Found.";
    private static final String MESSAGE_CONFLICT = "Request could not be processed.";
    private static final String MESSAGE_INTERNAL_ERROR = "An unexpected error occurred. Please try again.";

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ResponseMessage<BaseResult>> handleBadRequest(Exception exception) {
        LOGGER.warn("Bad request: {}", exception.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", MESSAGE_BAD_REQUEST);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public ResponseEntity<ResponseMessage<BaseResult>> handleValidation(Exception exception) {
        LOGGER.warn("Validation error: {}", exception.getMessage());
        ResponseMessage<BaseResult> response;
        if (exception instanceof MethodArgumentNotValidException ex) {
            response = ResponseMessageUtils.makeResponse(false, ex.getBindingResult());
        } else if (exception instanceof BindException ex) {
            response = ResponseMessageUtils.makeResponse(false, ex.getBindingResult());
        } else {
            response = ResponseMessageUtils.makeResponse(false, HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", MESSAGE_BAD_REQUEST);
        }
        attachBody(response, MESSAGE_BAD_REQUEST);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseMessage<BaseResult>> handleMalformedJson(HttpMessageNotReadableException exception) {
        LOGGER.warn("Malformed JSON: {}", exception.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", MESSAGE_MALFORMED_JSON);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ResponseMessage<BaseResult>> handleInvalidMultipart(Exception exception) {
        LOGGER.warn("Invalid multipart request: {}", exception.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", MESSAGE_BAD_REQUEST);
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
        return buildError(HttpStatus.CONFLICT, "CONFLICT", MESSAGE_CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseMessage<BaseResult>> handleUnexpected(Exception exception) {
        LOGGER.error("Unhandled exception", exception);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", MESSAGE_INTERNAL_ERROR);
    }

    private static ResponseEntity<ResponseMessage<BaseResult>> buildError(HttpStatus status, String code, String message) {
        ResponseMessage<BaseResult> response = ResponseMessageUtils.makeResponse(false, status.value(), code, message);
        attachBody(response, message);
        return ResponseEntity.status(status).body(response);
    }

    private static void attachBody(ResponseMessage<BaseResult> response, String message) {
        BaseResult body = new BaseResult();
        body.setStatus(false);
        body.setMessage(message);
        response.setBody(body);
    }
}
