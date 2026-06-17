package com.ut.emrPacs.helper;

import com.ut.emrPacs.config.ErrorReportingAttributes;
import com.ut.emrPacs.model.log.ActivityLog;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemErrorAlertServiceTest {

    @Test
    void sendsSanitizedTelegramAlertOncePerRequest() {
        TelegramHelper telegramHelper = mock(TelegramHelper.class);
        Environment environment = mock(Environment.class);
        when(environment.getProperty("server.from")).thenReturn("LOCAL");
        when(environment.getProperty("api.authUrl")).thenReturn("http://localhost:8080/pacsApi");

        SystemErrorAlertService service = new SystemErrorAlertService(telegramHelper, environment, "chat-1", true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/test");
        ActivityLog activityLog = new ActivityLog();
        activityLog.setId(15L);

        service.sendActivityErrorAlert(
                activityLog,
                "Admin <Root>",
                "/test/<bad>",
                "Test Module",
                "Unexpected <error>",
                42L,
                "java.lang.RuntimeException: <boom>",
                1L,
                "host-1",
                request
        );
        service.sendActivityErrorAlert(
                activityLog,
                "Admin",
                "/test",
                "Test Module",
                "Unexpected error",
                42L,
                "boom",
                1L,
                "host-1",
                request
        );

        verify(telegramHelper, times(1)).sendTextMessage(
                org.mockito.ArgumentMatchers.argThat(message ->
                        message.contains("<b>UDAYA PACS API Error</b>")
                                && message.contains("Admin &lt;Root&gt;")
                                && message.contains("/test/&lt;bad&gt;")
                                && message.contains("&lt;boom&gt;")
                ),
                eq("chat-1")
        );
        assertTrue(Boolean.TRUE.equals(request.getAttribute(ErrorReportingAttributes.ERROR_TELEGRAM_ALERTED)));
    }

    @Test
    void formatsSecurityIncidentAsSecurityAlert() {
        TelegramHelper telegramHelper = mock(TelegramHelper.class);
        Environment environment = mock(Environment.class);
        when(environment.getProperty("server.from")).thenReturn("LOCAL");

        SystemErrorAlertService service = new SystemErrorAlertService(telegramHelper, environment, "chat-1", true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/pacsApi/patient/patient-list");

        service.sendActivityErrorAlert(
                null,
                "Guest",
                "GET /patient/patient-list",
                "Security Monitor (Blocked Request)",
                "Security Threat Blocked",
                null,
                "SECURITY_EVENT event=threat_detected reason=sql-union detail=query",
                0L,
                "host-1",
                request
        );

        verify(telegramHelper).sendTextMessage(
                org.mockito.ArgumentMatchers.argThat(message ->
                        message.contains("<b>UDAYA PACS Security Alert</b>")
                                && message.contains("Security Threat Blocked")
                                && message.contains("sql-union")
                ),
                eq("chat-1")
        );
    }
}
