package com.ut.emrPacs.authentication.filter;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.cache.permission.EndpointPermissionCache;
import com.ut.emrPacs.cache.permission.PermissionCacheService;
import com.ut.emrPacs.mapper.auth.OAuth2ClientMapper;
import com.ut.emrPacs.model.permission.EndpointPermissionRule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModulePermissionFilterTest {

    private EndpointPermissionCache endpointPermissionCache;
    private PermissionCacheService permissionCacheService;
    private OAuth2ClientMapper oauth2ClientMapper;
    private ModulePermissionFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        endpointPermissionCache = Mockito.mock(EndpointPermissionCache.class);
        permissionCacheService = Mockito.mock(PermissionCacheService.class);
        oauth2ClientMapper = Mockito.mock(OAuth2ClientMapper.class);
        filter = new ModulePermissionFilter(endpointPermissionCache, permissionCacheService, oauth2ClientMapper);
        filterChain = Mockito.mock(FilterChain.class);

        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "denyWhenUnknown", false);
        ReflectionTestUtils.setField(filter, "skipPathsCsv", "/role/role-menu,/user/user-me,/user-profile/**");
        ReflectionTestUtils.setField(filter, "adminAuthoritiesCsv", "ROLE_ADMIN,ROLE_SUPER_ADMIN");
        ReflectionTestUtils.setField(filter, "clientAllowPathsCsv", "/worklist/worklist-received-study");
        ReflectionTestUtils.setField(filter, "clientAllowClientIdsCsv", "pacs-adapter");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipAuthPathWithoutAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/auth-login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturnForbiddenWhenPermissionMissing() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("100")
                .claim("principalType", "USER")
                .build();
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt, List.of());
        authenticationToken.setDetails(new CurrentUserPrincipal(100L, "admin", 11L, "H001", "pacs-web", "j1", 1L));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        EndpointPermissionRule rule = new EndpointPermissionRule();
        rule.setHttpMethod("POST");
        rule.setEndpointPattern("/patient/patient-list");
        rule.setPermissionCode("pacs.patient.view");

        when(endpointPermissionCache.resolveRules("POST", "/patient/patient-list")).thenReturn(List.of(rule));
        when(permissionCacheService.getPermissionCodes(100L, 11L, 1L)).thenReturn(Set.of("user.view"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/patient/patient-list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldAllowAdminAuthority() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("100")
                .claim("principalType", "USER")
                .build();
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        authenticationToken.setDetails(new CurrentUserPrincipal(100L, "admin", 11L, "H001", "pacs-web", "j1", 1L));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        EndpointPermissionRule rule = new EndpointPermissionRule();
        rule.setHttpMethod("POST");
        rule.setEndpointPattern("/patient/patient-list");
        rule.setPermissionCode("pacs.patient.view");

        when(endpointPermissionCache.resolveRules("POST", "/patient/patient-list")).thenReturn(List.of(rule));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/patient/patient-list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldBlockClientTokenOnNonAllowlistedPath() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("pacs-adapter")
                .claim("principalType", "CLIENT")
                .claim("clientId", "pacs-adapter")
                .build();
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_pacs.api")));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        EndpointPermissionRule rule = new EndpointPermissionRule();
        rule.setHttpMethod("POST");
        rule.setEndpointPattern("/worklist/worklist-list");
        rule.setPermissionCode("pacs.worklist.view");
        when(endpointPermissionCache.resolveRules("POST", "/worklist/worklist-list")).thenReturn(List.of(rule));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/worklist/worklist-list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldAllowClientTokenOnAllowlistedPathAndClientId() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("pacs-adapter")
                .claim("principalType", "CLIENT")
                .claim("clientId", "pacs-adapter")
                .build();
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_pacs.api")));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        EndpointPermissionRule rule = new EndpointPermissionRule();
        rule.setHttpMethod("POST");
        rule.setEndpointPattern("/worklist/worklist-received-study");
        rule.setRequiredScope("pacs.api");
        when(endpointPermissionCache.resolveRules("POST", "/worklist/worklist-received-study")).thenReturn(List.of(rule));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/worklist/worklist-received-study");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowGeneratedDicomServerClientOnCallbackPath() throws Exception {
        ReflectionTestUtils.setField(filter, "clientAllowClientIdsCsv", "");

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("dicom_server_server-4")
                .claim("principalType", "CLIENT")
                .claim("clientId", "dicom_server_server-4")
                .claim("dicomServerId", 4L)
                .build();
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_pacs.api")));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        EndpointPermissionRule rule = new EndpointPermissionRule();
        rule.setHttpMethod("POST");
        rule.setEndpointPattern("/worklist/worklist-received-study");
        rule.setRequiredScope("pacs.api");
        when(endpointPermissionCache.resolveRules("POST", "/worklist/worklist-received-study")).thenReturn(List.of(rule));
        when(oauth2ClientMapper.isActiveDicomServerCallbackClient("dicom_server_server-4")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/worklist/worklist-received-study");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }
}
