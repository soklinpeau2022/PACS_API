package com.ut.emrPacs.authentication.filter;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ut.emrPacs.mapper.auth.RevokedTokenMapper;
import com.ut.emrPacs.model.base.ResponseMessageUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs after {@code BearerTokenAuthenticationFilter} to verify the JWT's {@code jti} claim
 * has not been revoked (e.g. after logout).
 *
 * A revoked JTI is stored in the {@code revoked_tokens} table until the token's natural expiry.
 * Returns 401 and clears the security context if the JTI is found in that table.
 */
@Component
public class RevokedTokenFilter extends OncePerRequestFilter {

    private final RevokedTokenMapper revokedTokenMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RevokedTokenFilter(RevokedTokenMapper revokedTokenMapper) {
        this.revokedTokenMapper = revokedTokenMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth && jwtAuth.isAuthenticated()) {
            String jti = jwtAuth.getToken().getId();
            if (jti != null && !jti.isBlank()) {
                Long count = revokedTokenMapper.countByJti(jti);
                if (count != null && count > 0) {
                    SecurityContextHolder.clearContext();
                    writeUnauthorized(response);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String json = objectMapper.writeValueAsString(
                ResponseMessageUtils.makeResponse(false, 401, "UNAUTHORIZED", "Invalid or expired token")
        );
        response.getWriter().write(json);
    }
}
