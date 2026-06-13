package com.ut.emrPacs.authentication.util;

import com.ut.emrPacs.model.dto.response.authentication.token.AccessTokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ViewerAccessKeyService {
    public static final String CLIENT_ID = "pacs-viewer-api";
    public static final String SCOPE = "pacs.viewer.api";
    public static final String ACCESS_EDIT = "EDIT";
    public static final String ACCESS_READ = "READ";
    public static final String ACCESS_PUBLIC = "PUBLIC";

    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private JwtDecoder jwtDecoder;

    @Value("${pacs.viewer.api-key.edit-ms:86400000}")
    private long editLifetimeMs;
    @Value("${pacs.viewer.api-key.read-ms:86400000}")
    private long readLifetimeMs;
    @Value("${pacs.viewer.api-key.public-ms:86400000}")
    private long publicLifetimeMs;

    public String issue(
            Long hospitalId,
            Long worklistId,
            Long studyId,
            Long modalityId,
            String studyInstanceUid,
            Long userId,
            String username,
            String accessMode
    ) {
        String normalizedAccess = normalizeAccessMode(accessMode);
        long lifetimeMs = switch (normalizedAccess) {
            case ACCESS_EDIT -> editLifetimeMs;
            case ACCESS_PUBLIC -> publicLifetimeMs;
            default -> readLifetimeMs;
        };
        AccessTokenResponse response = jwtTokenService.issueViewerApiKey(
                hospitalId,
                worklistId,
                studyId,
                modalityId,
                studyInstanceUid,
                userId,
                username,
                normalizedAccess,
                lifetimeMs
        );
        return response == null ? null : response.getAccessToken();
    }

    public ViewerAccessClaims decode(String rawApiKey) {
        Jwt jwt = jwtDecoder.decode(extractKeyValue(rawApiKey));
        String clientId = jwt.getClaimAsString("clientId");
        String scope = jwt.getClaimAsString("scope");
        if (!CLIENT_ID.equals(clientId) || !hasScope(scope, SCOPE)) {
            throw new JwtException("Viewer API key scope is not valid.");
        }

        Long hospitalId = readLongClaim(jwt, "hospitalId");
        String studyInstanceUid = trimToNull(jwt.getClaimAsString("studyInstanceUid"));
        String accessMode = normalizeAccessMode(jwt.getClaimAsString("accessMode"));
        if (hospitalId == null || hospitalId <= 0L || studyInstanceUid == null) {
            throw new JwtException("Viewer API key binding is not valid.");
        }

        return new ViewerAccessClaims(
                hospitalId,
                readLongClaim(jwt, "worklistId"),
                readLongClaim(jwt, "studyId"),
                readLongClaim(jwt, "modalityId"),
                studyInstanceUid,
                readLongClaim(jwt, "userId"),
                trimToNull(jwt.getClaimAsString("username")),
                accessMode
        );
    }

    public static String normalizeAccessMode(String rawAccessMode) {
        String normalized = rawAccessMode == null ? "" : rawAccessMode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "WRITE", "AUTHOR", "OWNER", ACCESS_EDIT -> ACCESS_EDIT;
            case "PUBLIC", "PUBLIC_READ", "PUBLIC_READ_ONLY" -> ACCESS_PUBLIC;
            default -> ACCESS_READ;
        };
    }

    public static boolean canWrite(ViewerAccessClaims claims) {
        return claims != null && ACCESS_EDIT.equals(claims.accessMode());
    }

    public static boolean canRead(ViewerAccessClaims claims) {
        return claims != null
                && (ACCESS_EDIT.equals(claims.accessMode())
                || ACCESS_READ.equals(claims.accessMode())
                || ACCESS_PUBLIC.equals(claims.accessMode()));
    }

    public static boolean matchesScope(
            ViewerAccessClaims claims,
            Long hospitalId,
            Long worklistId,
            Long studyId,
            Long modalityId,
            String studyInstanceUid
    ) {
        if (claims == null || hospitalId == null || hospitalId <= 0L || !hospitalId.equals(claims.hospitalId())) {
            return false;
        }
        if (worklistId != null && worklistId > 0L && !worklistId.equals(claims.worklistId())) {
            return false;
        }
        if (studyId != null && studyId > 0L && !studyId.equals(claims.studyId())) {
            return false;
        }
        if (modalityId != null && modalityId > 0L && !modalityId.equals(claims.modalityId())) {
            return false;
        }
        if (studyInstanceUid != null && !studyInstanceUid.isBlank()
                && !studyInstanceUid.trim().equals(claims.studyInstanceUid())) {
            return false;
        }
        return true;
    }

    private static String extractKeyValue(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return "";
        }
        String value = rawApiKey.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return value.substring("Bearer ".length()).trim();
        }
        if (value.regionMatches(true, 0, "ApiKey ", 0, "ApiKey ".length())) {
            return value.substring("ApiKey ".length()).trim();
        }
        return value;
    }

    private static boolean hasScope(String scope, String expectedScope) {
        if (scope == null || expectedScope == null) {
            return false;
        }
        String[] scopes = scope.trim().split("\\s+");
        for (String item : scopes) {
            if (expectedScope.equals(item)) {
                return true;
            }
        }
        return false;
    }

    private static Long readLongClaim(Jwt jwt, String claimName) {
        Object value = jwt.getClaim(claimName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record ViewerAccessClaims(
            Long hospitalId,
            Long worklistId,
            Long studyId,
            Long modalityId,
            String studyInstanceUid,
            Long userId,
            String username,
            String accessMode
    ) {
    }
}
