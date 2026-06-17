package com.ut.emrPacs.helper.security;

import com.ut.emrPacs.service.service.ActivityLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SecurityIncidentReporterTest {

    @Test
    void writesSecurityIncidentToActivityLogWithSanitizedDetail() throws Exception {
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        SecurityIncidentReporter reporter = new SecurityIncidentReporter(activityLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/auth/auth-login");
        request.setRemoteAddr("192.0.2.50");

        reporter.reportBlockedRequest(request, "threat_detected", "sql-union", "password=secret\r\ntoken=abc123");

        ArgumentCaptor<String> bugCaptor = ArgumentCaptor.forClass(String.class);
        verify(activityLogService).insert(
                eq("POST /pacsApi/auth/auth-login"),
                eq(null),
                bugCaptor.capture(),
                eq("Security Monitor"),
                eq("Security Monitor (Blocked Request)"),
                eq("Block"),
                eq(2),
                eq("Security Threat Blocked"),
                any(LocalTime.class),
                any(LocalTime.class),
                eq(request)
        );

        String bug = bugCaptor.getValue();
        assertTrue(bug.contains("SECURITY_EVENT event=threat_detected reason=sql-union"));
        assertTrue(bug.contains("password=[redacted]"));
        assertTrue(bug.contains("token=[redacted]"));
        assertFalse(bug.contains("abc123"));
        assertEquals(-1, bug.indexOf('\n'));
    }
}
