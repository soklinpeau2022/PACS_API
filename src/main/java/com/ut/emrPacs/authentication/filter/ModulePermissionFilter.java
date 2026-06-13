package com.ut.emrPacs.authentication.filter;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.cache.permission.EndpointPermissionCache;
import com.ut.emrPacs.cache.permission.PermissionCacheService;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.mapper.auth.OAuth2ClientMapper;
import com.ut.emrPacs.model.permission.EndpointPermissionRule;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ModulePermissionFilter extends OncePerRequestFilter {

    private static final Set<String> ALWAYS_SKIP_PREFIXES = Set.of(
            "/auth/",
            "/actuator/",
            "/swagger-ui",
            "/api-docs",
            "/v3/api-docs"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Value("${app.security.permissions.enabled:true}")
    private boolean enabled;

    @Value("${app.security.permissions.deny-when-unknown:false}")
    private boolean denyWhenUnknown;

    @Value("${app.security.permissions.skip-paths:" + ApiConstants.Role.MENU_FULL_PATH + "," + ApiConstants.User.ME_FULL_PATH + ",/role/role-menu,/user/me,/user-profile/**}")
    private String skipPathsCsv;

    @Value("${app.security.admin.authorities:ROLE_ADMIN,ROLE_SUPER_ADMIN}")
    private String adminAuthoritiesCsv;

    @Value("${app.security.permissions.client-allow-paths:" + ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.RECEIVED_STUDY_PATH + "}")
    private String clientAllowPathsCsv;

    @Value("${app.security.permissions.client-allow-client-ids:}")
    private String clientAllowClientIdsCsv;

    private final EndpointPermissionCache endpointPermissionCache;
    private final PermissionCacheService permissionCacheService;
    private final OAuth2ClientMapper oauth2ClientMapper;

    private volatile List<String> skipPatterns = null;
    private volatile List<String> adminAuthorities = null;
    private volatile List<String> clientAllowPatterns = null;
    private volatile Set<String> clientAllowedClientIds = null;

    public ModulePermissionFilter(EndpointPermissionCache endpointPermissionCache,
                                  PermissionCacheService permissionCacheService,
                                  OAuth2ClientMapper oauth2ClientMapper) {
        this.endpointPermissionCache = endpointPermissionCache;
        this.permissionCacheService = permissionCacheService;
        this.oauth2ClientMapper = oauth2ClientMapper;
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = normalizePath(request);
        if (path.isEmpty() || shouldSkipPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || isAnonymous(auth)) {
            filterChain.doFilter(request, response);
            return;
        }

        EndpointPermissionRule endpointRule = endpointPermissionCache.resolveRule(request.getMethod(), path);
        if (endpointRule == null) {
            if (denyWhenUnknown) {
                writeForbidden(response, "Forbidden");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        String requiredScope = endpointRule.getRequiredScope();
        if (requiredScope != null && !requiredScope.isBlank() && !hasScopeAuthority(auth, requiredScope)) {
            writeForbidden(response, "Forbidden");
            return;
        }

        if (hasAnyAuthority(auth, getAdminAuthorities())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isClientToken(auth)) {
            if (!isAllowedClientRequest(path, auth)) {
                writeForbidden(response, "Forbidden");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        PrincipalSnapshot principal = extractPrincipal(auth);
        if (principal.userId == null || principal.hospitalId == null) {
            writeForbidden(response, "Forbidden");
            return;
        }

        String permissionCode = endpointRule.getPermissionCode();
        if (permissionCode == null || permissionCode.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Set<String> permissionCodes = permissionCacheService.getPermissionCodes(
                principal.userId,
                principal.hospitalId,
                principal.permissionVersion
        );

        if (permissionCodes.contains(permissionCode)) {
            filterChain.doFilter(request, response);
            return;
        }

        writeForbidden(response, "Forbidden");
    }

    private boolean shouldSkipPath(String path) {
        if (path.equals("/error")) {
            return true;
        }
        for (String prefix : ALWAYS_SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        for (String pattern : getSkipPatterns()) {
            if (!pattern.isEmpty() && PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getSkipPatterns() {
        List<String> cached = skipPatterns;
        if (cached != null) {
            return cached;
        }
        skipPatterns = parseCsv(skipPathsCsv);
        return skipPatterns;
    }

    private List<String> getAdminAuthorities() {
        List<String> cached = adminAuthorities;
        if (cached != null) {
            return cached;
        }
        adminAuthorities = parseCsv(adminAuthoritiesCsv);
        return adminAuthorities;
    }

    private List<String> getClientAllowPatterns() {
        List<String> cached = clientAllowPatterns;
        if (cached != null) {
            return cached;
        }
        clientAllowPatterns = parseCsv(clientAllowPathsCsv);
        return clientAllowPatterns;
    }

    private Set<String> getClientAllowedClientIds() {
        Set<String> cached = clientAllowedClientIds;
        if (cached != null) {
            return cached;
        }
        clientAllowedClientIds = parseCsvLower(clientAllowClientIdsCsv);
        return clientAllowedClientIds;
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static Set<String> parseCsvLower(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String token : csv.split(",")) {
            String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
        return Collections.unmodifiableSet(values);
    }

    private static String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private static boolean isAnonymous(Authentication auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_ANONYMOUS".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyAuthority(Authentication auth, List<String> expected) {
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        for (GrantedAuthority grantedAuthority : auth.getAuthorities()) {
            String value = grantedAuthority.getAuthority();
            if (value == null) {
                continue;
            }
            for (String role : expected) {
                if (value.equals(role)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasScopeAuthority(Authentication auth, String requiredScope) {
        String expected = "SCOPE_" + requiredScope.trim();
        for (GrantedAuthority grantedAuthority : auth.getAuthorities()) {
            if (expected.equals(grantedAuthority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClientToken(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return ApiConstants.Security.PRINCIPAL_TYPE_CLIENT.equals(jwtAuth.getToken().getClaimAsString("principalType"));
        }
        return false;
    }

    private boolean isAllowedClientRequest(String path, Authentication auth) {
        boolean pathAllowed = false;
        for (String pattern : getClientAllowPatterns()) {
            if (!pattern.isEmpty() && PATH_MATCHER.match(pattern, path)) {
                pathAllowed = true;
                break;
            }
        }
        if (!pathAllowed) {
            return false;
        }

        String clientId = extractClientId(auth);
        if (clientId == null || clientId.isBlank()) {
            return false;
        }
        String normalizedClientId = clientId.trim().toLowerCase(Locale.ROOT);

        Set<String> allowedClientIds = getClientAllowedClientIds();
        if (allowedClientIds.contains(normalizedClientId)) {
            return true;
        }

        try {
            return Boolean.TRUE.equals(oauth2ClientMapper.isActiveDicomServerCallbackClient(clientId.trim()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String extractClientId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getClaimAsString("clientId");
        }
        return null;
    }

    private static PrincipalSnapshot extractPrincipal(Authentication auth) {
        if (auth.getDetails() instanceof CurrentUserPrincipal currentUserPrincipal) {
            return new PrincipalSnapshot(
                    currentUserPrincipal.userId(),
                    currentUserPrincipal.hospitalId(),
                    currentUserPrincipal.permissionVersion()
            );
        }
        return new PrincipalSnapshot(null, null, null);
    }

    private static void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"message\":\"" + message.replace("\"", "") + "\"}");
    }

    private record PrincipalSnapshot(Long userId, Long hospitalId, Long permissionVersion) {
    }
}
