package com.ut.emrPacs.helper.security;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ut.emrPacs.helper.http.RequestClientInfoHelper;
import com.ut.emrPacs.model.users.CustomUserDetails;

import jakarta.servlet.http.HttpServletRequest;

public final class SecurityAuditLogger {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SecurityAuditLogger() {
    }

    public static void logBlocked(Logger logger, HttpServletRequest request, String event, String reason, String detail) {
        if (logger == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", event);
            payload.put("reason", reason);
            if (detail != null && !detail.isBlank()) {
                payload.put("detail", detail);
            }
            if (request != null) {
                payload.put("method", request.getMethod());
                payload.put("path", request.getRequestURI());
                payload.put("ip", RequestClientInfoHelper.resolveClientIp(request));
                payload.put("userAgent", request.getHeader("User-Agent"));
            }
            Long userId = resolveUserId();
            if (userId != null) {
                payload.put("userId", userId);
            }
            String json = OBJECT_MAPPER.writeValueAsString(payload);
            logger.warn("SECURITY_EVENT {}", json);
        } catch (Exception ex) {
            logger.warn("SECURITY_EVENT event={} reason={} detail={}", event, reason, detail);
        }
    }

    /**
     * Logs a per-record sensitive data access (HIPAA-style audit trail).
     * Call this whenever a patient / medical record is read.
     */
    public static void logDataAccess(Logger logger, HttpServletRequest request, String resourceType, Object resourceId) {
        if (logger == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "data_access");
            payload.put("resource", resourceType);
            payload.put("resourceId", resourceId);
            Long userId = resolveUserId();
            if (userId != null) {
                payload.put("userId", userId);
            }
            if (request != null) {
                payload.put("ip", RequestClientInfoHelper.resolveClientIp(request));
                payload.put("userAgent", request.getHeader("User-Agent"));
            }
            String json = OBJECT_MAPPER.writeValueAsString(payload);
            logger.info("AUDIT {}", json);
        } catch (Exception ex) {
            logger.info("AUDIT event=data_access resource={} resourceId={}", resourceType, resourceId);
        }
    }

    private static Long resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.id();
        }
        return null;
    }
}
