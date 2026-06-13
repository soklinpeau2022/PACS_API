package com.ut.emrPacs.authentication.util;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;

/**
 * Converts a validated JWT into a {@link JwtAuthenticationToken} and attaches a
 * {@link CurrentUserPrincipal} as {@code details} so that downstream filters
 * (e.g. {@code ModulePermissionFilter}) can read userId, hospitalId, etc.
 *
 * Authorities populated:
 * - SCOPE_xxx  from the {@code scope} claim (space-separated scopes)
 * - ROLE_xxx   from the {@code roles} claim  (comma-separated roles)
 */
@Component
public class PacsJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // Map space-separated scope claim → SCOPE_xxx authorities
        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isBlank()) {
            for (String s : scope.trim().split("\\s+")) {
                if (!s.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
                }
            }
        }

        // Map comma-separated roles claim → role authorities
        String roles = jwt.getClaimAsString("roles");
        if (roles != null && !roles.isBlank()) {
            for (String role : roles.trim().split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(trimmed));
                }
            }
        }

        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, authorities);

        // Build CurrentUserPrincipal for downstream filters (e.g. ModulePermissionFilter)
        String principalType = jwt.getClaimAsString("principalType");
        Long userId = null;
        if ("USER".equals(principalType)) {
            try { userId = Long.valueOf(jwt.getSubject()); } catch (Exception ignored) {}
        }

        Long hospitalId   = toLong(jwt.getClaim("hospitalId"));
        Long permVersion  = toLong(jwt.getClaim("permissionVersion"));
        String hospitalCode = jwt.getClaimAsString("hospitalCode");
        String clientId     = jwt.getClaimAsString("clientId");
        String username     = jwt.getClaimAsString("username");
        String jti          = jwt.getId();

        token.setDetails(new CurrentUserPrincipal(
                userId, username, hospitalId, hospitalCode, clientId, jti, permVersion));

        return token;
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof Number n) return n.longValue();
        try { return Long.valueOf(value.toString()); } catch (Exception e) { return null; }
    }
}
