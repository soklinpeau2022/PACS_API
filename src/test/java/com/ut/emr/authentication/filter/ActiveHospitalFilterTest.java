package com.ut.emrPacs.authentication.filter;

import com.ut.emrPacs.mapper.auth.HospitalSecurityMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ActiveHospitalFilterTest {

    private HospitalSecurityMapper hospitalSecurityMapper;
    private ActiveHospitalFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        hospitalSecurityMapper = Mockito.mock(HospitalSecurityMapper.class);
        filter = new ActiveHospitalFilter(hospitalSecurityMapper);
        filterChain = Mockito.mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldBlockWhenHospitalIsInactive() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("100")
                .claim("principalType", "USER")
                .claim("hospitalId", 11L)
                .build();
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt);
        authenticationToken.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        when(hospitalSecurityMapper.countActiveHospitalById(11L)).thenReturn(0L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/patient/patient-list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldPassWhenHospitalAndMembershipAreActive() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("100")
                .claim("principalType", "USER")
                .claim("hospitalId", 11L)
                .build();
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt);
        authenticationToken.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        when(hospitalSecurityMapper.countActiveHospitalById(11L)).thenReturn(1L);
        when(hospitalSecurityMapper.countActiveUserHospital(100L, 11L)).thenReturn(1L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/patient/patient-list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowClientCallbackWithoutHospitalClaim() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("dicomserver-adapter")
                .claim("principalType", "CLIENT")
                .build();
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt);
        authenticationToken.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/worklist/worklist-received-study");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldBlockClientWithoutHospitalClaimOnProtectedPath() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("dicomserver-adapter")
                .claim("principalType", "CLIENT")
                .build();
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt);
        authenticationToken.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/worklist/worklist-list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(403, response.getStatus());
    }
}
