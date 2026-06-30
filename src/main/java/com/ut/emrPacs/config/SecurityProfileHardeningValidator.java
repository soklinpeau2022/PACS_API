package com.ut.emrPacs.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
public class SecurityProfileHardeningValidator implements InitializingBean {

    private final Environment environment;

    @Value("${security.jwt.auto-generate-keys:true}")
    private boolean autoGenerateJwtKeys;

    @Value("${security.jwt.private-key:}")
    private String privateKeyLocation;

    @Value("${security.jwt.public-key:}")
    private String publicKeyLocation;

    @Value("${security.jwt.key-id:}")
    private String jwtKeyId;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${cors.allowedOrigins:}")
    private String corsAllowedOrigins;

    @Value("${cors.allowCredentials:false}")
    private boolean corsAllowCredentials;

    @Value("${api.authUrl:}")
    private String apiAuthUrl;

    @Value("${pacs.result.static-auth.enabled:true}")
    private boolean pacsResultStaticAuthEnabled;

    @Value("${pacs.result.static-auth.api-key:}")
    private String pacsResultApiKey;

    @Value("${security.jwt.refresh-token-allow-body:false}")
    private boolean refreshTokenAllowBody;

    @Value("${security.jwt.refresh-token-return-in-body:false}")
    private boolean refreshTokenReturnInBody;

    @Value("${security.jwt.access-token-return-in-body:false}")
    private boolean accessTokenReturnInBody;

    @Value("${security.jwt.refresh-token-cookie-name:}")
    private String refreshTokenCookieName;

    @Value("${security.jwt.refresh-token-cookie-same-site:}")
    private String refreshTokenCookieSameSite;

    @Value("${security.jwt.refresh-token-cookie-secure:false}")
    private boolean refreshTokenCookieSecure;

    @Value("${app.security.permissions.deny-when-unknown:false}")
    private boolean denyWhenUnknownPermission;

    @Value("${app.security.permissions.client-allow-paths:}")
    private String clientAllowPaths;

    @Value("${spring.flyway.clean-disabled:true}")
    private boolean flywayCleanDisabled;

    @Value("${spring.flyway.clean-on-validation-error:false}")
    private boolean flywayCleanOnValidationError;

    @Value("${spring.flyway.validate-on-migrate:true}")
    private boolean flywayValidateOnMigrate;

    @Value("${spring.flyway.out-of-order:false}")
    private boolean flywayOutOfOrder;

    @Value("${spring.flyway.baseline-on-migrate:false}")
    private boolean flywayBaselineOnMigrate;

    @Value("${spring.flyway.fail-on-missing-locations:true}")
    private boolean flywayFailOnMissingLocations;

    public SecurityProfileHardeningValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!isStrictProfile()) {
            return;
        }

        if (autoGenerateJwtKeys) {
            throw new IllegalStateException("security.jwt.auto-generate-keys must be false in qa/prod.");
        }

        assertPemKeyPath("security.jwt.private-key", privateKeyLocation);
        assertPemKeyPath("security.jwt.public-key", publicKeyLocation);

        if (isMissingOrPlaceholder(jwtKeyId)) {
            throw new IllegalStateException("SECURITY_JWT_KEY_ID must be set in qa/prod.");
        }
        if (isMissingOrPlaceholder(datasourcePassword)) {
            throw new IllegalStateException("SPRING_DATASOURCE_PASSWORD must be set in qa/prod.");
        }
        if (isMissingOrPlaceholder(corsAllowedOrigins) || isLocalUrl(corsAllowedOrigins)) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must be set and must not use localhost or Docker hostnames.");
        }
        if (corsAllowCredentials && containsWildcardOrigin(corsAllowedOrigins)) {
            throw new IllegalStateException(
                    "CORS_ALLOW_CREDENTIALS must not be combined with a wildcard '*' origin in qa/prod "
                            + "(reflecting any origin with credentials defeats CORS). List explicit origins instead.");
        }
        if (isMissingOrPlaceholder(apiAuthUrl) || isLocalUrl(apiAuthUrl)) {
            throw new IllegalStateException("API_AUTH_URL must be set to the public API server URL in qa/prod.");
        }
        if (pacsResultStaticAuthEnabled && isMissingOrPlaceholder(pacsResultApiKey)) {
            throw new IllegalStateException("PACS_RESULT_API_KEY must be set when PACS result static auth is enabled in qa/prod.");
        }
        if (refreshTokenAllowBody) {
            throw new IllegalStateException("security.jwt.refresh-token-allow-body must be false in qa/prod; use the HttpOnly refresh cookie.");
        }
        if (refreshTokenReturnInBody) {
            throw new IllegalStateException("security.jwt.refresh-token-return-in-body must be false in qa/prod; refresh tokens must not be readable by browser JavaScript.");
        }
        if (!accessTokenReturnInBody) {
            throw new IllegalStateException("security.jwt.access-token-return-in-body must stay true until access-token cookie auth is implemented.");
        }
        if (isMissingOrPlaceholder(refreshTokenCookieName)) {
            throw new IllegalStateException("security.jwt.refresh-token-cookie-name must be set in qa/prod.");
        }
        if (!isSupportedSameSite(refreshTokenCookieSameSite)) {
            throw new IllegalStateException("security.jwt.refresh-token-cookie-same-site must be Strict, Lax, or None.");
        }
        if ("none".equalsIgnoreCase(refreshTokenCookieSameSite) && !refreshTokenCookieSecure) {
            throw new IllegalStateException("security.jwt.refresh-token-cookie-secure must be true when SameSite=None.");
        }
        if (!refreshTokenCookieSecure && !isPrivateLanHttpUrl(apiAuthUrl)) {
            throw new IllegalStateException("security.jwt.refresh-token-cookie-secure must be true outside private .lan HTTP deployments.");
        }
        if (!denyWhenUnknownPermission) {
            throw new IllegalStateException("app.security.permissions.deny-when-unknown must be true in qa/prod.");
        }
        if (isMissingOrPlaceholder(clientAllowPaths) || clientAllowPaths.contains("*")) {
            throw new IllegalStateException("APP_SECURITY_CLIENT_ALLOW_PATHS must be explicit and not '*'.");
        }
        if (!flywayCleanDisabled) {
            throw new IllegalStateException("spring.flyway.clean-disabled must be true in qa/prod.");
        }
        if (flywayCleanOnValidationError) {
            throw new IllegalStateException("spring.flyway.clean-on-validation-error must be false in qa/prod.");
        }
        if (!flywayValidateOnMigrate) {
            throw new IllegalStateException("spring.flyway.validate-on-migrate must be true in qa/prod.");
        }
        if (flywayOutOfOrder) {
            throw new IllegalStateException("spring.flyway.out-of-order must be false in qa/prod.");
        }
        if (flywayBaselineOnMigrate) {
            throw new IllegalStateException("spring.flyway.baseline-on-migrate must be false in qa/prod.");
        }
        if (!flywayFailOnMissingLocations) {
            throw new IllegalStateException("spring.flyway.fail-on-missing-locations must be true in qa/prod.");
        }
    }

    private boolean isStrictProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile == null ? "" : profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(profile -> "qa".equals(profile) || "prod".equals(profile) || "production".equals(profile));
    }

    private static void assertPemKeyPath(String keyName, String value) {
        String normalized = value == null ? "" : value.trim();
        boolean valid = normalized.startsWith("file:/app/config/key/")
                && normalized.endsWith(".pem");
        if (!valid) {
            throw new IllegalStateException(keyName + " must point to file:/app/config/key/*.pem in qa/prod.");
        }
    }

    private static boolean isMissingOrPlaceholder(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("change_me")
                || normalized.startsWith("replace_with_")
                || normalized.contains("example.com")
                || "change".equals(normalized);
    }

    private static boolean containsWildcardOrigin(String value) {
        if (value == null) {
            return false;
        }
        return Arrays.stream(value.split(","))
                .map(entry -> entry == null ? "" : entry.trim())
                .anyMatch("*"::equals);
    }

    private static boolean isLocalUrl(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("localhost")
                || normalized.contains("127.0.0.1")
                || normalized.contains(".docker.internal")
                || normalized.contains("dicom_server_ksfh")
                || normalized.contains("dicom_server_nmchc")
                || normalized.contains("udaya_dicom_server_ksfh")
                || normalized.contains("udaya_dicom_server_nmchc");
    }

    private static boolean isSupportedSameSite(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "strict".equals(normalized) || "lax".equals(normalized) || "none".equals(normalized);
    }

    private static boolean isPrivateLanHttpUrl(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") && normalized.contains(".lan");
    }
}
