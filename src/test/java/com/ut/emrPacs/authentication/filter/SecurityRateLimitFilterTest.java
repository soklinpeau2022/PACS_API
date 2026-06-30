package com.ut.emrPacs.authentication.filter;

import com.ut.emrPacs.helper.security.SecurityIncidentReporter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

class SecurityRateLimitFilterTest {

    private SecurityRateLimitFilter filter;
    private SecurityIncidentReporter securityIncidentReporter;

    @BeforeEach
    void setUp() {
        securityIncidentReporter = Mockito.mock(SecurityIncidentReporter.class);
        filter = new SecurityRateLimitFilter(securityIncidentReporter);
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "authWindowSeconds", 60);
        ReflectionTestUtils.setField(filter, "authMaxRequests", 2);
        ReflectionTestUtils.setField(filter, "loginWindowSeconds", 60);
        ReflectionTestUtils.setField(filter, "loginMaxRequests", 1);
        ReflectionTestUtils.setField(filter, "publicViewerWindowSeconds", 300);
        ReflectionTestUtils.setField(filter, "publicViewerMaxRequests", 2);
    }

    @Test
    void shouldSkipRateLimitForNonAuthPath() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/patient/patient-list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldBlockAfterAuthRateLimitExceeded() throws Exception {
        FilterChain chain1 = Mockito.mock(FilterChain.class);
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/auth/auth-refresh");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(req1, res1, chain1);

        FilterChain chain2 = Mockito.mock(FilterChain.class);
        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/auth/auth-refresh");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, chain2);

        FilterChain chain3 = Mockito.mock(FilterChain.class);
        MockHttpServletRequest req3 = new MockHttpServletRequest("POST", "/auth/auth-refresh");
        MockHttpServletResponse res3 = new MockHttpServletResponse();
        filter.doFilter(req3, res3, chain3);

        assertEquals(200, res1.getStatus());
        assertEquals(200, res2.getStatus());
        assertEquals(429, res3.getStatus());
        verify(securityIncidentReporter).reportBlockedRequest(req3, "rate_limit", "auth", "/auth/auth-refresh");
    }

    @Test
    void shouldApplyStrictLoginBucketForAuthLoginEndpoint() throws Exception {
        ReflectionTestUtils.setField(filter, "authMaxRequests", 100);
        ReflectionTestUtils.setField(filter, "loginMaxRequests", 1);

        FilterChain chain1 = Mockito.mock(FilterChain.class);
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/auth/auth-login");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(req1, res1, chain1);

        FilterChain chain2 = Mockito.mock(FilterChain.class);
        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/auth/auth-login");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, chain2);

        assertEquals(200, res1.getStatus());
        assertEquals(429, res2.getStatus());
    }

    @Test
    void shouldThrottlePublicViewerPhoneVerification() throws Exception {
        String path = "/pacs-result-api/public-viewer-authorize";

        MockHttpServletResponse first = invoke(path);
        MockHttpServletResponse second = invoke(path);
        MockHttpServletResponse third = invoke(path);

        assertEquals(200, first.getStatus());
        assertEquals(200, second.getStatus());
        assertEquals(429, third.getStatus());
    }

    @Test
    void shouldNotCountCorsPreflightAgainstPublicViewerLimit() throws Exception {
        String path = "/pacs-result-api/public-viewer-authorize";

        MockHttpServletResponse preflight = invoke("OPTIONS", path);
        MockHttpServletResponse first = invoke(path);
        MockHttpServletResponse second = invoke(path);
        MockHttpServletResponse third = invoke(path);

        assertEquals(200, preflight.getStatus());
        assertEquals(200, first.getStatus());
        assertEquals(200, second.getStatus());
        assertEquals(429, third.getStatus());
    }

    private MockHttpServletResponse invoke(String path) throws Exception {
        return invoke("POST", path);
    }

    private MockHttpServletResponse invoke(String method, String path) throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
