package com.ut.emrPacs.config;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

@RestController
public class ApiErrorController implements ErrorController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiErrorController.class);

    private final ErrorAttributes errorAttributes;
    private final MessageService messageService;

    public ApiErrorController(ErrorAttributes errorAttributes, MessageService messageService) {
        this.errorAttributes = errorAttributes;
        this.messageService = messageService;
    }

    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<ResponseMessage<BaseResult>> handleError(HttpServletRequest request) {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        // Direct requests to /error may not include an upstream status; avoid defaulting to 500.
        int statusCode = (statusObj instanceof Integer status) ? status : 404;
        HttpStatus httpStatus = HttpStatus.resolve(statusCode);

        String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (path == null) {
            path = request.getRequestURI();
        }

        Throwable error = errorAttributes.getError(new ServletWebRequest(request));
        if (error != null) {
            LOGGER.error("Unhandled error on {} (status {}): {}", path, statusCode, error, error);
        } else {
            LOGGER.error("Unhandled error on {} (status {})", path, statusCode);
        }

        String errorText = httpStatus != null ? httpStatus.getReasonPhrase() : "Error";
        String userMessage = (statusCode == 404)
                ? "Not Found"
                : "An unexpected error occurred. Please try again.";

        ResponseMessage<BaseResult> response = ResponseMessageUtils.makeResponse(
                false,
                messageService.message(userMessage, null, false)
        );
        response.getHeader().setStatusCode(statusCode);
        response.getHeader().setErrorText(errorText);

        HttpStatus fallbackStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(httpStatus != null ? httpStatus : fallbackStatus).body(response);
    }
}
