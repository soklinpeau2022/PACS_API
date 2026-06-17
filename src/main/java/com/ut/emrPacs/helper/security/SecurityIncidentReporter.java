package com.ut.emrPacs.helper.security;

import com.ut.emrPacs.helper.http.RequestClientInfoHelper;
import com.ut.emrPacs.service.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class SecurityIncidentReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityIncidentReporter.class);
    private static final int MAX_VALUE_LENGTH = 500;
    private static final Pattern SECRET_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)\\b(authorization|cookie|set-cookie|password|passwd|token|secret|api[_-]?key)\\b\\s*[:=]\\s*[^\\s,;]+"
    );

    private final ActivityLogService activityLogService;

    public SecurityIncidentReporter(@Lazy ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    public void reportBlockedRequest(HttpServletRequest request, String event, String reason, String detail) {
        HttpServletRequest resolvedRequest = resolveRequest(request);
        String safeEvent = sanitize(event, "security_event");
        String safeReason = sanitize(reason, "blocked");
        String safeDetail = sanitize(detail, "");
        String endpoint = resolveEndpoint(resolvedRequest);
        String bug = buildBug(resolvedRequest, safeEvent, safeReason, safeDetail);

        try {
            LocalTime startedAt = LocalTime.now();
            activityLogService.insert(
                    endpoint,
                    null,
                    bug,
                    "Security Monitor",
                    "Security Monitor (Blocked Request)",
                    "Block",
                    2,
                    "Security Threat Blocked",
                    startedAt,
                    LocalTime.now(),
                    resolvedRequest
            );
        } catch (Exception ex) {
            LOGGER.warn(
                    "Security incident activity log failed: event={} reason={} endpoint={} error={}",
                    safeEvent,
                    safeReason,
                    endpoint,
                    ex.getMessage()
            );
        }
    }

    private static HttpServletRequest resolveRequest(HttpServletRequest request) {
        if (request != null) {
            return request;
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String resolveEndpoint(HttpServletRequest request) {
        if (request == null) {
            return "SECURITY_GUARD";
        }
        String method = sanitize(request.getMethod(), "UNKNOWN").toUpperCase(Locale.ROOT);
        String path = sanitize(request.getRequestURI(), "/");
        return method + " " + path;
    }

    private static String buildBug(HttpServletRequest request, String event, String reason, String detail) {
        StringBuilder bug = new StringBuilder();
        bug.append("SECURITY_EVENT event=").append(event);
        bug.append(" reason=").append(reason);
        if (detail != null && !detail.isBlank()) {
            bug.append(" detail=").append(detail);
        }
        if (request != null) {
            bug.append(" ip=").append(sanitize(RequestClientInfoHelper.resolveClientIp(request), "unknown"));
            bug.append(" method=").append(sanitize(request.getMethod(), "UNKNOWN").toUpperCase(Locale.ROOT));
            bug.append(" path=").append(sanitize(request.getRequestURI(), "/"));
        }
        return clip(bug.toString(), MAX_VALUE_LENGTH);
    }

    private static String sanitize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String cleaned = value
                .replaceAll("[\\x00-\\x1F\\x7F]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        cleaned = SECRET_ASSIGNMENT_PATTERN.matcher(cleaned).replaceAll("$1=[redacted]");
        return clip(cleaned, MAX_VALUE_LENGTH);
    }

    private static String clip(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
