package com.ut.emrPacs.authentication.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.mapper.auth.HospitalSecurityMapper;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class ActiveHospitalFilter extends OncePerRequestFilter {

    private static final Set<String> SKIP_PREFIXES = Set.of(
            "/auth/",
            "/actuator/",
            "/swagger-ui",
            "/api-docs",
            "/v3/api-docs",
            "/error"
    );
    private static final Set<String> CLIENT_NO_HOSPITAL_ALLOWED_PATHS = Set.of(
            "/worklist/worklist-received-study"
    );

    private final HospitalSecurityMapper hospitalSecurityMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ActiveHospitalFilter(HospitalSecurityMapper hospitalSecurityMapper) {
        this.hospitalSecurityMapper = hospitalSecurityMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = normalizePath(request);
        if (shouldSkip(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth) || !jwtAuth.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String principalType = jwtAuth.getToken().getClaimAsString("principalType");
        if ("CLIENT".equals(principalType) && CLIENT_NO_HOSPITAL_ALLOWED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long hospitalId = toLong(jwtAuth.getToken().getClaim("hospitalId"));
        if (hospitalId == null || hospitalId <= 0) {
            writeForbidden(response);
            return;
        }

        Long activeHospital = hospitalSecurityMapper.countActiveHospitalById(hospitalId);
        if (activeHospital == null || activeHospital <= 0) {
            writeForbidden(response);
            return;
        }

        if ("USER".equals(principalType)) {
            Long userId = toLong(jwtAuth.getToken().getSubject());
            if (userId == null || userId <= 0) {
                writeForbidden(response);
                return;
            }

            Long activeUserHospital = hospitalSecurityMapper.countActiveUserHospital(userId, hospitalId);
            if (activeUserHospital == null || activeUserHospital <= 0) {
                writeForbidden(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean shouldSkip(String path) {
        if (path == null || path.isBlank()) {
            return true;
        }
        if (isAuthPath(path)) {
            return true;
        }
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAuthPath(String path) {
        return path.startsWith(ApiConstants.Auth.BASE_PATH + "/")
                || path.contains(ApiConstants.Auth.BASE_PATH + "/");
    }

    private static String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String json = objectMapper.writeValueAsString(
                ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden")
        );
        response.getWriter().write(json);
    }
}
