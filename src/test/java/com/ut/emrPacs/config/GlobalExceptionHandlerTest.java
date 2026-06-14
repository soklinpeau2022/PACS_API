package com.ut.emrPacs.config;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

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
}
