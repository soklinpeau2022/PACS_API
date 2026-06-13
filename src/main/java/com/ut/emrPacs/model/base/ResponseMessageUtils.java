package com.ut.emrPacs.model.base;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class for constructing standard API response messages.
 */
public abstract class ResponseMessageUtils {
    private static final String DEFAULT_CLIENT_ERROR_MESSAGE = "Error";
    private static final int MAX_CLIENT_ERROR_MESSAGE_LENGTH = 200;

    private static void markRollbackOnlyIfNeeded(boolean success) {
        // Business validation failures are normal API responses. Real exceptions remain
        // the transaction rollback signal.
    }

    private static String sanitizeClientErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return DEFAULT_CLIENT_ERROR_MESSAGE;
        }
        String trimmed = message.trim();
        if (trimmed.length() > MAX_CLIENT_ERROR_MESSAGE_LENGTH) {
            return DEFAULT_CLIENT_ERROR_MESSAGE;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);

        // Do not expose internal details (SQL, stack traces, package/class names, framework errors)
        if (lower.contains("exception")
                || lower.contains("java.")
                || lower.contains("jakarta.")
                || lower.contains("javax.")
                || lower.contains("org.")
                || lower.contains("com.")
                || lower.contains("springframework")
                || lower.contains("mybatis")
                || lower.contains("hibernate")
                || lower.contains("jdbc")
                || lower.contains("sql")
                || lower.contains("select ")
                || lower.contains("insert ")
                || lower.contains("update ")
                || lower.contains("delete ")
                || lower.contains("from ")
                || lower.contains("where ")
                || lower.contains("duplicate entry")
                || lower.contains("constraint")
                || lower.contains("foreign key")
                || trimmed.contains("###")
                || trimmed.contains("\n")
                || trimmed.contains("\r")
                || lower.startsWith("error:")
                || lower.startsWith("database error:")
                || lower.startsWith("error during")) {
            return DEFAULT_CLIENT_ERROR_MESSAGE;
        }

        return trimmed;
    }

    /**
     * Create a success response with a body.
     *
     * @param body the response body
     * @return the response message
     */
    public static <T> ResponseMessage<T> makeSuccessResponse(T body) {
        ResponseMessage<T> response = new ResponseMessage<>();
        ResponseHeader header = response.getHeader();
        header.setResult(true);
        header.setStatusCode(HttpStatus.OK.value());
        if (!(body instanceof Boolean)) {
            response.setBody(body);
        }
        return response;
    }

    /**
     * Create a success response with token and pagination.
     *
     * @param body       the response body
     * @param token      the authentication token
     * @param pagination pagination info
     * @return the response message
     */
    public static <T> ResponseMessage<T> makeSuccessResponse(T body, String token, Pagination pagination) {
        ResponseMessage<T> response = new ResponseMessage<>();
        ResponseHeader header = response.getHeader();
        header.setResult(true);
        header.setStatusCode(HttpStatus.OK.value());
        header.setToken(token);
        header.setPagination(pagination);
        if (!(body instanceof Boolean)) {
            response.setBody(body);
        }
        return response;
    }

    /**
     * Create a response with a specified success status and body.
     *
     * @param success whether the operation succeeded
     * @param body    the response body
     * @return the response message
     */
    public static <T> ResponseMessage<T> makeResponse(boolean success, T body) {
        markRollbackOnlyIfNeeded(success);
        int statusCode = success ? HttpStatus.OK.value() : HttpStatus.BAD_REQUEST.value();
        return makeResponse(success, statusCode, body);
    }

    /**
     * Create a response with a specified success status, HTTP status code, and body.
     *
     * @param success whether the operation succeeded
     * @param statusCode the HTTP status code
     * @param body the response body
     * @return the response message
     */
    public static <T> ResponseMessage<T> makeResponse(boolean success, int statusCode, T body) {
        markRollbackOnlyIfNeeded(success);
        ResponseMessage<T> response = new ResponseMessage<>();
        ResponseHeader header = response.getHeader();
        header.setResult(success);
        header.setStatusCode(statusCode);
        response.setBody(body);
        return response;
    }

    /**
     * Create a response with custom status code and error info.
     *
     * @param success    whether the operation succeeded
     * @param statusCode the HTTP status code
     * @param errorCode  the error code (can be null)
     * @param errorText  the error message
     * @return the response message
     */
    public static <T> ResponseMessage<T> makeResponse(boolean success, int statusCode, String errorCode, String errorText) {
        markRollbackOnlyIfNeeded(success);
        ResponseMessage<T> response = new ResponseMessage<>();
        ResponseHeader header = response.getHeader();
        header.setResult(success);
        header.setStatusCode(statusCode);
        header.setErrorCode(errorCode);
        header.setErrorText(success ? errorText : sanitizeClientErrorMessage(errorText));
        return response;
    }

    /**
     * Create a response from validation errors.
     *
     * @param success        whether the operation succeeded
     * @param bindingResult  the validation result
     * @return the response message
     */
    public static <T> ResponseMessage<T> makeResponse(boolean success, BindingResult bindingResult) {
        markRollbackOnlyIfNeeded(success);
        ResponseMessage<T> response = new ResponseMessage<>();
        Map<String, String> errorMessageList = new HashMap<>();
        for (Object object : bindingResult.getAllErrors()) {
            if (object instanceof FieldError fieldError) {
                errorMessageList.put(fieldError.getField(), fieldError.getDefaultMessage());
            }
        }
        ResponseHeader header = response.getHeader();
        header.setResult(success);
        header.setStatusCode(HttpStatus.BAD_REQUEST.value());
        header.setErrorText(new JSONObject(errorMessageList).toString());
        return response;
    }

    /**
     * Create a response with a BaseResult as the body.
     *
     * @param success    whether the operation succeeded
     * @param baseResult the result object
     * @return the response message
     */
    @SuppressWarnings("unchecked")
    public static <T> ResponseMessage<T> makeResponse(boolean success, BaseResult baseResult) {
        markRollbackOnlyIfNeeded(success);
        ResponseMessage<T> response = new ResponseMessage<>();
        ResponseHeader header = response.getHeader();
        header.setResult(success);
        header.setStatusCode(success ? HttpStatus.OK.value() : HttpStatus.BAD_REQUEST.value());
        if (baseResult != null) {
            // Keep response envelope as the single source of status/result.
            baseResult.setStatus(null);
        }
        if (!success && baseResult != null) {
            String sanitized = sanitizeClientErrorMessage(baseResult.getMessage());
            header.setErrorText(sanitized);
            baseResult.setMessage(null);
        }
        response.setBody((T) baseResult);
        return response;
    }

}
