package com.ut.emrPacs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ut.emrPacs.helper.security.SecurityPayloadSanitizerHelper;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

@ControllerAdvice
public class ResponseBodySanitizerAdvice implements ResponseBodyAdvice<Object> {
    private static final ObjectMapper DOCS_MAPPER = new ObjectMapper();

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) {
            return body;
        }

        String path = request.getURI().getPath();
        if (isDocsPath(path)) {
            return body;
        }

        if (shouldSkipBody(body)) {
            return body;
        }

        if (shouldSkipPath(path)) {
            return body;
        }

        return SecurityPayloadSanitizerHelper.sanitizeInPlace(body);
    }

    private boolean shouldSkipBody(Object body) {
        return body instanceof byte[]
                || body instanceof Resource
                || body instanceof StreamingResponseBody;
    }

    private boolean shouldSkipPath(String path) {
        if (path == null) {
            return false;
        }

        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.contains("/swagger-ui")
                || normalized.contains("/dicom-routing/dicom-routing-build-config/");
    }

    private boolean isDocsPath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.contains("/api-docs")
                || normalized.contains("/v3/api-docs");
    }

    private Object tryDecodeOpenApiBody(Object body) {
        try {
            if (body instanceof String raw) {
                String trimmed = raw.trim();
                if (trimmed.startsWith("{\"openapi\"")) {
                    return DOCS_MAPPER.readTree(trimmed);
                }
                if (trimmed.startsWith("eyJvcGVuYXBpIjo") || trimmed.startsWith("\"eyJvcGVuYXBpIjo")) {
                    String token = trimmed;
                    if (token.startsWith("\"") && token.endsWith("\"") && token.length() > 1) {
                        token = token.substring(1, token.length() - 1);
                    }
                    byte[] decoded = Base64.getUrlDecoder().decode(token);
                    String decodedJson = new String(decoded, StandardCharsets.UTF_8);
                    if (decodedJson.startsWith("{\"openapi\"")) {
                        return DOCS_MAPPER.readTree(decodedJson);
                    }
                }
            }
            if (body instanceof byte[] bytes) {
                String asText = new String(bytes, StandardCharsets.UTF_8).trim();
                if (asText.startsWith("{\"openapi\"")) {
                    return DOCS_MAPPER.readTree(asText);
                }
            }
        } catch (Exception ignored) {
            // keep original body when probe/decoding fails
        }
        return body;
    }
}
