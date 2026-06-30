package com.ut.emrPacs.authentication.filter;

import com.ut.emrPacs.mapper.auth.RevokedTokenMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevokedTokenFilterTest {

    private RevokedTokenMapper revokedTokenMapper;
    private RevokedTokenFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        revokedTokenMapper = Mockito.mock(RevokedTokenMapper.class);
        filter = new RevokedTokenFilter(revokedTokenMapper);
        chain = Mockito.mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnUnauthorizedWhenJwtJtiRevoked() throws Exception {
        JwtAuthenticationToken jwtAuth = Mockito.mock(JwtAuthenticationToken.class);
        var jwt = Mockito.mock(org.springframework.security.oauth2.jwt.Jwt.class);
        when(jwtAuth.isAuthenticated()).thenReturn(true);
        when(jwtAuth.getToken()).thenReturn(jwt);
        when(jwt.getId()).thenReturn("revoked-jti");
        SecurityContextHolder.getContext().setAuthentication(jwtAuth);
        when(revokedTokenMapper.countByJti("revoked-jti")).thenReturn(1L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/patient/patient-list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldAllowWhenJwtJtiNotRevoked() throws Exception {
        JwtAuthenticationToken jwtAuth = Mockito.mock(JwtAuthenticationToken.class);
        var jwt = Mockito.mock(org.springframework.security.oauth2.jwt.Jwt.class);
        when(jwtAuth.isAuthenticated()).thenReturn(true);
        when(jwtAuth.getToken()).thenReturn(jwt);
        when(jwt.getId()).thenReturn("active-jti");
        SecurityContextHolder.getContext().setAuthentication(jwtAuth);
        when(revokedTokenMapper.countByJti("active-jti")).thenReturn(0L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/patient/patient-list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }
}
