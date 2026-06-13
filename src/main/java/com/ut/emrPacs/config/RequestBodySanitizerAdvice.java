package com.ut.emrPacs.config;

import com.ut.emrPacs.helper.security.RequestPayloadGuard;
import com.ut.emrPacs.helper.security.SecurityPayloadSanitizerHelper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;
import java.util.Set;

@ControllerAdvice
public class RequestBodySanitizerAdvice extends RequestBodyAdviceAdapter {

    private final Validator validator;

    public RequestBodySanitizerAdvice(Validator validator) {
        this.validator = validator;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        Object sanitized = SecurityPayloadSanitizerHelper.sanitizeInPlace(body);
        RequestPayloadGuard.validate(sanitized);
        validatePayload(sanitized);
        return sanitized;
    }

    private void validatePayload(Object body) {
        if (body == null || validator == null) {
            return;
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
