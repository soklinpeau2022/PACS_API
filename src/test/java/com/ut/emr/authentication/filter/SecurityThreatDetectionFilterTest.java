package com.ut.emrPacs.authentication.filter;

import com.ut.emrPacs.helper.security.SecurityIncidentReporter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SecurityThreatDetectionFilterTest {

    private SecurityThreatDetectionFilter filter;
    private FilterChain filterChain;
    private SecurityIncidentReporter securityIncidentReporter;

    @BeforeEach
    void setUp() {
        securityIncidentReporter = Mockito.mock(SecurityIncidentReporter.class);
        filter = new SecurityThreatDetectionFilter(securityIncidentReporter);
        filterChain = Mockito.mock(FilterChain.class);
    }

    @Test
    void shouldBlockSqlInjectionInQuery() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/patient/patient-list");
        request.setQueryString("searchText=' OR 1=1 --");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(400, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
        verify(securityIncidentReporter).reportBlockedRequest(request, "threat_detected", "sql-boolean-num", "query");
    }

    @Test
    void shouldBlockXssInJsonBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/auth-login");
        request.setContentType("application/json");
        request.setContent("{\"username\":\"admin\",\"password\":\"<script>alert(1)</script>\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(400, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldBlockCrossSiteWhenAuthCookieExistsAndOriginMismatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/auth-refresh");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.addHeader("Origin", "http://evil.example");
        request.setCookies(new jakarta.servlet.http.Cookie("refreshToken", "tkn"));
        request.setContentType("application/json");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldAllowConfiguredFrontendOriginWhenAuthCookieExists() throws Exception {
        ReflectionTestUtils.setField(filter, "allowedOriginsCsv", "http://localhost:4173");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/user-me");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.addHeader("Origin", "http://localhost:4173");
        request.setCookies(new jakarta.servlet.http.Cookie("refreshToken", "tkn"));
        request.setContentType("application/json");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(Mockito.any(), Mockito.eq(response));
        verify(securityIncidentReporter, never()).reportBlockedRequest(Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldNotTreatWildcardCorsOriginAsTrustedForCookieRequests() throws Exception {
        ReflectionTestUtils.setField(filter, "allowedOriginsCsv", "*");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/user-me");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.addHeader("Origin", "http://evil.example");
        request.setCookies(new jakarta.servlet.http.Cookie("refreshToken", "tkn"));
        request.setContentType("application/json");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldAllowSafeRequestBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/auth-login");
        request.setContentType("application/json");
        request.setContent("{\"clientId\":\"pacs-web\",\"username\":\"admin\",\"password\":\"1\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(Mockito.any(), Mockito.eq(response));
    }

    @Test
    void shouldAllowDicomServerCallbackUrlConfigFields() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/dicom-server/dicom-server-update");
        request.setContentType("application/json");
        request.setContent(("""
                {
                  "id": 4,
                  "name": "DicomServer KSFH",
                  "baseUrl": "http://localhost:8042",
                  "dicomServerUiBaseUrl": "http://localhost:8042",
                  "dicomwebBaseUrl": "http://localhost:8042/dicom-web",
                  "viewerBaseUrl": "http://localhost:3005",
                  "pacsApiCallbackBaseUrl": "http://localhost:8080/pacsApi"
                }
                """).getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(Mockito.any(), Mockito.eq(response));
    }

    @Test
    void shouldAllowPrettyPrintedJsonBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/user-list");
        request.setContentType("application/json");
        request.setContent(("""
                {
                  "page": 0,
                  "rowsPerPage": 10,
                  "searchText": ""
                }
                """).getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(Mockito.any(), Mockito.eq(response));
    }

    @Test
    void shouldBlockDoubleEncodedPathTraversalInQuery() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/file/file-upload/test.jpg");
        request.setQueryString("path=%252e%252e%252fetc%252fpasswd");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(400, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldBlockPayloadTooLarge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/user-create");
        request.setContentType("application/json");
        byte[] huge = new byte[(1024 * 1024) + 64];
        request.setContent(huge);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(413, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldAllowViewerStatePayloadAboveGenericJsonLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/pacsApi/pacs-result-api/pacs-result-viewer-state-save"
        );
        request.setContentType("application/json");
        String payload = "{\"annotations\":[{\"data\":{\"label\":\""
                + "a".repeat((1024 * 1024) + 64)
                + "\"}}]}";
        request.setContent(payload.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(Mockito.any(), Mockito.eq(response));
    }

    @Test
    void shouldTreatViewerAnnotationTextAsStoredDataInsteadOfExecutableSql() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/pacsApi/pacs-result-api/pacs-result-viewer-state-save"
        );
        request.setContentType("application/json");
        request.setContent(
                "{\"annotations\":[{\"data\":{\"label\":\"rule out DROP TABLE artifact\"}}]}"
                        .getBytes(StandardCharsets.UTF_8)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(Mockito.any(), Mockito.eq(response));
    }

    @Test
    void shouldBlockHostHeaderInjection() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/study/study-list");
        request.addHeader("Host", "example.com\r\nX-Injected: yes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(400, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }
}
