package com.ut.emrPacs.helper;

import com.ut.emrPacs.config.ErrorReportingAttributes;
import com.ut.emrPacs.model.log.ActivityLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Component
public class SystemErrorAlertService {

    private static final int TELEGRAM_MESSAGE_LIMIT = 3900;

    private final TelegramHelper telegramHelper;
    private final Environment environment;
    private final String telegramChatId;
    private final boolean alertsEnabled;

    public SystemErrorAlertService(
            TelegramHelper telegramHelper,
            Environment environment,
            @Value("${telegram.chat.id:}") String telegramChatId,
            @Value("${telegram.alerts.enabled:true}") boolean alertsEnabled
    ) {
        this.telegramHelper = telegramHelper;
        this.environment = environment;
        this.telegramChatId = telegramChatId;
        this.alertsEnabled = alertsEnabled;
    }

    public void sendActivityErrorAlert(
            ActivityLog activityLog,
            String username,
            String endpoint,
            String moduleId,
            String description,
            Long line,
            String bug,
            long durationSeconds,
            String hostname,
            HttpServletRequest request
    ) {
        if (!shouldSend(request)) {
            return;
        }

        String message = buildMessage(
                activityLog != null && activityLog.getId() != null ? String.valueOf(activityLog.getId()) : "-",
                "DB activity log saved",
                endpoint,
                moduleId,
                description,
                line,
                bug,
                username,
                durationSeconds,
                hostname,
                null
        );
        send(message, request);
    }

    public void sendAuditFailureAlert(
            String endpoint,
            Long line,
            String bug,
            String moduleName,
            String moduleId,
            String act,
            String description,
            LocalTime startDuration,
            LocalTime endDuration,
            HttpServletRequest request,
            Throwable auditFailure
    ) {
        if (!shouldSend(request)) {
            return;
        }

        String fallbackModule = nonBlank(moduleId, nonBlank(moduleName, "Unknown Module") + " (" + nonBlank(act, "unknown") + ")");
        String fallbackBug = nonBlank(bug, "Unknown Error");
        long durationSeconds = safeDurationSeconds(startDuration, endDuration);
        String auditFailureMessage = auditFailure == null ? "" : auditFailure.toString();

        String message = buildMessage(
                "-",
                "DB activity log failed; Telegram fallback sent",
                endpoint,
                fallbackModule,
                description,
                line,
                fallbackBug,
                "Unknown",
                durationSeconds,
                property("HOSTNAME", property("COMPUTERNAME", "unknown")),
                auditFailureMessage
        );
        send(message, request);
    }

    private boolean shouldSend(HttpServletRequest request) {
        if (!alertsEnabled) {
            log.debug("Skip Telegram error alert: telegram.alerts.enabled=false.");
            return false;
        }
        if (ErrorReportingAttributes.isErrorTelegramAlerted(request)) {
            return false;
        }
        return true;
    }

    private void send(String message, HttpServletRequest request) {
        try {
            ErrorReportingAttributes.markErrorTelegramAlerted(request);
            telegramHelper.sendTextMessage(clip(message), telegramChatId);
        } catch (Exception error) {
            log.warn("Telegram error alert failed: {}", error.getMessage());
        }
    }

    private String buildMessage(
            String activityId,
            String auditStatus,
            String endpoint,
            String moduleId,
            String description,
            Long line,
            String bug,
            String username,
            long durationSeconds,
            String hostname,
            String auditFailure
    ) {
        String server = property("server.from", "UNKNOWN");
        String apiUrl = property("api.authUrl", property("springdoc.server-url", ""));
        StringBuilder message = new StringBuilder();
        message.append("<b>").append(isSecurityAlert(moduleId, description, bug)
                ? "UDAYA PACS Security Alert"
                : "UDAYA PACS API Error").append("</b>\n");
        message.append("<b>Server:</b> ").append(html(server)).append("\n");
        if (!apiUrl.isBlank()) {
            message.append("<b>API:</b> ").append(html(apiUrl)).append("\n");
        }
        message.append("<b>Time:</b> ").append(html(LocalDateTime.now().toString())).append("\n");
        message.append("<b>Activity ID:</b> ").append(html(activityId)).append("\n");
        message.append("<b>Audit:</b> ").append(html(auditStatus)).append("\n");
        message.append("<b>Endpoint:</b> <code>").append(html(nonBlank(endpoint, "-"))).append("</code>\n");
        message.append("<b>Module:</b> ").append(html(nonBlank(moduleId, "-"))).append("\n");
        message.append("<b>Description:</b> ").append(html(nonBlank(description, "Error"))).append("\n");
        message.append("<b>User:</b> ").append(html(nonBlank(username, "Unknown"))).append("\n");
        message.append("<b>Hostname:</b> ").append(html(nonBlank(hostname, "unknown"))).append("\n");
        message.append("<b>Duration:</b> ").append(durationSeconds).append(" seconds\n");
        message.append("<b>Line:</b> ").append(line != null ? line : "-").append("\n");
        message.append("<b>Bug:</b> ").append(html(nonBlank(bug, "Unknown Error")));
        if (auditFailure != null && !auditFailure.isBlank()) {
            message.append("\n<b>Audit Failure:</b> ").append(html(auditFailure));
        }
        return message.toString();
    }

    private static boolean isSecurityAlert(String moduleId, String description, String bug) {
        return containsIgnoreCase(moduleId, "security")
                || containsIgnoreCase(description, "security")
                || containsIgnoreCase(bug, "security_event");
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null
                && needle != null
                && value.toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT));
    }

    private String property(String key, String defaultValue) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String nonBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static long safeDurationSeconds(LocalTime startDuration, LocalTime endDuration) {
        if (startDuration == null || endDuration == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(startDuration, endDuration).toSeconds());
    }

    private static String clip(String message) {
        if (message == null || message.length() <= TELEGRAM_MESSAGE_LIMIT) {
            return message;
        }
        return message.substring(0, TELEGRAM_MESSAGE_LIMIT) + "...";
    }

    private static String html(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
