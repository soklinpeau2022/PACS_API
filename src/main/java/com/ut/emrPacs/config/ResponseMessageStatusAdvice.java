package com.ut.emrPacs.config;

import com.ut.emrPacs.model.base.ResponseHeader;
import com.ut.emrPacs.model.base.ResponseMessage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
@NullMarked
public class ResponseMessageStatusAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> parameterType = returnType.getParameterType();
        return ResponseMessage.class.isAssignableFrom(parameterType);
    }

    @Override
    public @Nullable Object beforeBodyWrite(
            @Nullable Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (!(body instanceof ResponseMessage<?> responseMessage)) {
            return body;
        }

        ResponseHeader header = responseMessage.getHeader();
        Integer statusCode = header != null ? header.getStatusCode() : null;
        if (statusCode != null) {
            try {
                response.setStatusCode(HttpStatusCode.valueOf(statusCode));
                return body;
            } catch (IllegalArgumentException ignored) {
                // fallback below
            }
        }

        if (header != null && Boolean.FALSE.equals(header.getResult())) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
        }
        return body;
    }
}
