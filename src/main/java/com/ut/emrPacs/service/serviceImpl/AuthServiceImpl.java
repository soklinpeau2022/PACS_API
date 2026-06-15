package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.util.*;
import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.config.ApiConstants;
import static com.ut.emrPacs.helper.FunctionHelper.trimToNull;
import com.ut.emrPacs.helper.http.RequestClientInfoHelper;
import com.ut.emrPacs.helper.security.SecurityAuditLogger;
import com.ut.emrPacs.mapper.auth.AuthAuditMapper;
import com.ut.emrPacs.mapper.auth.AuthMapper;
import com.ut.emrPacs.mapper.auth.OAuth2ClientMapper;
import com.ut.emrPacs.mapper.auth.RevokedTokenMapper;
import com.ut.emrPacs.mapper.user.UserMapper;
import com.ut.emrPacs.model.auth.OAuth2ClientRow;
import com.ut.emrPacs.model.auth.RefreshTokenRow;
import com.ut.emrPacs.model.dto.request.authentication.token.ClientCredentialsRequest;
import com.ut.emrPacs.model.dto.request.authentication.login.LoginRequest;
import com.ut.emrPacs.model.dto.request.authentication.token.RefreshTokenRequest;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.response.authentication.token.AccessTokenResponse;
import com.ut.emrPacs.model.users.CustomUserDetails;
import com.ut.emrPacs.model.users.User;
import com.ut.emrPacs.model.users.UserGroupList;
import com.ut.emrPacs.service.service.AuthService;
import com.ut.emrPacs.service.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.*;

import static com.ut.emrPacs.config.ApiConstants.Security.PRINCIPAL_TYPE_CLIENT;
import static com.ut.emrPacs.config.ApiConstants.Security.PRINCIPAL_TYPE_USER;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private AuthMapper authMapper;
    @Autowired
    private AuthAuditMapper authAuditMapper;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private OAuth2ClientMapper oauth2ClientMapper;
    @Autowired
    private RevokedTokenMapper revokedTokenMapper;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private MessageService messageService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ActivityLogService activityLogService;
    @Autowired
    private RefreshTokenCryptoService refreshTokenCryptoService;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private LoginAttemptTracker loginAttemptTracker;
    @Autowired
    private PasswordEncoder passwordEncoder;
    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

    @Value("${security.jwt.refresh-token-ms}")
    private long refreshTokenExpirationMs;

    @Value("${security.jwt.refresh-token-allow-body:true}")
    private boolean allowRefreshTokenInBody;

    @Value("${security.jwt.refresh-token-return-in-body:true}")
    private boolean returnRefreshTokenInBody;

    @Value("${security.jwt.access-token-return-in-body:true}")
    private boolean returnAccessTokenInBody;

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> handleLogin(LoginRequest loginRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        LocalTime startDuration = LocalTime.now();
        if (loginRequest == null) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
        }
        normalizeLoginRequest(loginRequest);
        String clientIp = RequestClientInfoHelper.resolveClientIp(httpServletRequest);
        String userAgent = RequestClientInfoHelper.formatUserAgent(httpServletRequest);
        String targetUsername = resolveLoginIdentifier(loginRequest);
        String clientId = trimToNull(loginRequest.getClientId());
        if (clientId == null) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("clientId is required.", false));
        }

        if (targetUsername.isBlank()) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("Username is required.", false));
        }
        if (trimToNull(loginRequest.getPassword()) == null) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("Password is required.", false));
        }

        try {
            OAuth2ClientRow clientRow = validateOAuthClientForGrant(clientId, "password_login", false);
            if (clientRow == null) {
                auditAuthEvent("invalid_client", false, null, clientId, PRINCIPAL_TYPE_USER, null, httpServletRequest, "invalid_client_or_grant");
                return ResponseMessageUtils.makeResponse(false, 401, messageService.message("Invalid client.", false));
            }

            // Login no longer accepts custom requested scopes from payload.
            // Default to the client's allowed scopes so normal endpoint access still works.
            ScopeResolution scopeResolution = resolveGrantedScope(null, clientRow);
            if (!scopeResolution.valid()) {
                auditAuthEvent("invalid_scope", false, null, clientId, PRINCIPAL_TYPE_USER, null, httpServletRequest, scopeResolution.reasonCode());
                return ResponseMessageUtils.makeResponse(false, 403, messageService.message("Invalid scope.", false));
            }

            if (loginAttemptTracker.isLocked(clientIp, targetUsername)) {
                long remaining = loginAttemptTracker.getLockRemainingSeconds(clientIp, targetUsername);
                String reason = loginAttemptTracker.getLockReason(clientIp, targetUsername);
                SecurityAuditLogger.logBlocked(logger, httpServletRequest, "account_lockout", reason, "locked_for_" + remaining + "s");
                auditAuthEvent("login_failure", false, null, clientId, PRINCIPAL_TYPE_USER, null, httpServletRequest, "account_locked");
                return ResponseMessageUtils.makeResponse(false, messageService.message(
                        "Too many failed login attempts. Please try again in " + remaining + " seconds.", false));
            }

            clearExpiredTokens(httpServletRequest, httpServletResponse);

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(targetUsername, loginRequest.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            Long userId = extractUserId(authentication);
            if (userId != null) {
                Long expiredCount = authAuditMapper.countExpiredUser(userId);
                if (expiredCount != null && expiredCount > 0) {
                    SecurityContextHolder.clearContext();
                    auditAuthEvent("login_failure", false, userId, clientId, PRINCIPAL_TYPE_USER, null, httpServletRequest, "user_expired");
                    return ResponseMessageUtils.makeResponse(false, messageService.message("This user is already expired. Please contact administrator.", false));
                }
            }

            loginAttemptTracker.recordSuccess(clientIp, targetUsername);

            String subject = authentication.getName();
            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(Objects::nonNull)
                    .toList();
            Long hospitalId = resolveHospitalIdForUser(userId);
            if (hospitalId == null) {
                SecurityContextHolder.clearContext();
                auditAuthEvent("login_failure", false, userId, clientId, PRINCIPAL_TYPE_USER, null, httpServletRequest, "no_hospital");
                return ResponseMessageUtils.makeResponse(false, messageService.message("No active hospital assigned to this user.", false));
            }

            String hospitalCode = authMapper.findHospitalCodeByHospitalId(hospitalId);
            Long permissionVersion = resolvePermissionVersion(userId);
            String rolesCsv = String.join(",", roles);
            long accessTokenLifetimeMs = resolveAccessTokenLifetimeMs(clientRow);
            Long refreshTokenLifetimeMs = resolveRefreshTokenLifetimeMs(clientRow);

            AccessTokenResponse accessTokenResponse = jwtTokenService.issueUserAccessToken(
                    userId, subject, clientId, hospitalId, hospitalCode,
                    scopeResolution.grantedScope(), permissionVersion, rolesCsv, accessTokenLifetimeMs);
            List<AccessTokenResponse> tokens = new ArrayList<>();
            tokens.add(accessTokenResponse);
            replaceWithOpaqueRefreshToken(tokens, userId, hospitalId, clientId, loginRequest.getClientName(), httpServletRequest, null, refreshTokenLifetimeMs);
            String tokenJti = extractAccessTokenJti(tokens);
            stripBrowserTokensFromResponse(tokens);
            setNoStoreHeaders(httpServletResponse);

            if (userId != null) {
                try {
                    authAuditMapper.updateUserLoginAudit(userId, clientIp, userAgent);
                    authAuditMapper.insertUserLog(userId, "Login", userAgent, clientIp);
                } catch (Exception auditError) {
                    logger.warn("Login audit failed for userId={}: {}", userId, auditError.toString());
                }
            }

            auditAuthEvent("login_success", true, userId, clientId, PRINCIPAL_TYPE_USER, tokenJti, httpServletRequest, "ok");

            BaseResult result = new BaseResult();
            result.setData(tokens);

            LocalTime endDuration = LocalTime.now();
            try {
                activityLogService.insert(ApiConstants.Auth.LOGIN_FULL_PATH, null, null, "Authentication", "Authentication (Login)", "Login",
                        1, "Success", startDuration, endDuration, httpServletRequest);
            } catch (Exception ex) {
                logger.warn("Activity log insert failed (login success): {}", ex.getMessage());
            }
            return ResponseMessageUtils.makeResponse(true, result);
        } catch (AuthenticationException e) {
            loginAttemptTracker.recordFailure(clientIp, targetUsername);
            auditAuthEvent("login_failure", false, null, clientId, PRINCIPAL_TYPE_USER, null, httpServletRequest, "invalid_credentials");
            logger.warn("Login authentication failed: {}", e.getMessage());
            LocalTime endDuration = LocalTime.now();
            try {
                activityLogService.insert(ApiConstants.Auth.LOGIN_FULL_PATH, null, e.toString(), "Authentication", "Authentication (Login)", "Login",
                        2, "Invalid Credentials", startDuration, endDuration, httpServletRequest);
            } catch (Exception ex) {
                logger.warn("Activity log insert failed (login invalid credentials): {}", ex.getMessage());
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid User Name or Password.", false));
        } catch (Exception e) {
            auditAuthEvent("login_failure", false, null, clientId, PRINCIPAL_TYPE_USER, null, httpServletRequest, "internal_error");
            logger.error("Login failed: {}", e.toString(), e);
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (e.getStackTrace() != null && e.getStackTrace().length > 0)
                    ? (long) e.getStackTrace()[0].getLineNumber()
                    : null;
            try {
                activityLogService.insert(ApiConstants.Auth.LOGIN_FULL_PATH, errorLine, e.toString(), "Authentication", "Authentication (Login)", "Login",
                        2, "Error", startDuration, endDuration, httpServletRequest);
            } catch (Exception ex) {
                logger.warn("Activity log insert failed (login error): {}", ex.getMessage());
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Login failed.", false));
        }
    }

    /**
     * Cleans up expired tokens before login.
     */
    private void clearExpiredTokens(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        try { revokedTokenMapper.deleteExpired(LocalDateTime.now()); } catch (Exception ex) { logger.warn("Expired token cleanup failed: {}", ex.getMessage()); }
        refreshTokenService.cleanupExpired();
        SecurityContextHolder.clearContext();
    }


    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> handleLogout(RefreshTokenRequest refreshTokenRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        // Process flow: execute handle logout business logic and return operation result.
        LocalTime startDuration = LocalTime.now();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = extractUserId(authentication);
        try {
            revokeCurrentAccessTokenIfPresent(httpServletRequest);
            revokeRefreshTokenIfPresent(refreshTokenRequest, httpServletRequest);

            if (userId != null) {
                try {
                    String remoteIp = RequestClientInfoHelper.resolveClientIp(httpServletRequest);
                    String httpUserAgent = RequestClientInfoHelper.formatUserAgent(httpServletRequest);
                    authAuditMapper.clearUserLoginAudit(userId);
                    // Insert user log
                    authAuditMapper.insertUserLog(userId, "LogOut", httpUserAgent, remoteIp);
                } catch (Exception auditError) {
                    logger.warn("Logout audit failed for userId={}: {}", userId, auditError.toString());
                }
            }

            auditAuthEvent("logout", true, userId, null, PRINCIPAL_TYPE_USER, null, httpServletRequest, "ok");

            LocalTime endDuration = LocalTime.now();
            try {
                activityLogService.insert(ApiConstants.Auth.LOGOUT_FULL_PATH, null, null, "Authentication", "Authentication (Logout)", "Logout",
                        1, "Success", startDuration, endDuration, httpServletRequest);
            } catch (Exception ex) {
                logger.warn("Activity log insert failed (logout success): {}", ex.getMessage());
            }

            return ResponseMessageUtils.makeResponse(true, messageService.message("Logout successful. You need to log in again.", true));
        } catch (Exception e) {
            auditAuthEvent("logout", false, userId, null, PRINCIPAL_TYPE_USER, null, httpServletRequest, "internal_error");
            logger.error("Logout failed: {}", e.toString());
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (e.getStackTrace() != null && e.getStackTrace().length > 0)
                    ? (long) e.getStackTrace()[0].getLineNumber()
                    : null;
            try {
                activityLogService.insert(ApiConstants.Auth.LOGOUT_FULL_PATH, errorLine, e.toString(), "Authentication", "Authentication (Logout)", "Logout",
                        2, "Error", startDuration, endDuration, httpServletRequest);
            } catch (Exception ex) {
                logger.warn("Activity log insert failed (logout error): {}", ex.getMessage());
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Logout failed.", false));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> refreshAccessToken(RefreshTokenRequest refreshTokenRequest, HttpServletRequest request, HttpServletResponse response) {
        String requestedClientId = refreshTokenRequest != null ? trimToNull(refreshTokenRequest.getClientId()) : null;
        String refreshToken = resolveRefreshToken(refreshTokenRequest, request);
        if (refreshToken == null || refreshToken.isBlank()) {
            auditAuthEvent("refresh_failure", false, null, requestedClientId, PRINCIPAL_TYPE_USER, null, request, "missing_refresh_token");
            return ResponseMessageUtils.makeResponse(false, 401, messageService.message("Invalid or expired token", false));
        }

        RefreshTokenRow refreshTokenRow = refreshTokenService.validate(refreshToken);
        if (refreshTokenRow == null) {
            auditAuthEvent("refresh_failure", false, null, requestedClientId, PRINCIPAL_TYPE_USER, null, request, "invalid_refresh_token");
            return ResponseMessageUtils.makeResponse(false, 401, messageService.message("Invalid or expired token", false));
        }

        String refreshClientId = trimToNull(refreshTokenRow.getClientId());
        if (requestedClientId != null && !requestedClientId.equals(refreshClientId)) {
            auditAuthEvent("refresh_failure", false, refreshTokenRow.getUserId(), requestedClientId, PRINCIPAL_TYPE_USER, null, request, "client_mismatch");
            return ResponseMessageUtils.makeResponse(false, 401, messageService.message("Invalid client.", false));
        }

        OAuth2ClientRow clientRow = validateOAuthClientForGrant(refreshClientId, "refresh_token", false);
        if (clientRow == null) {
            auditAuthEvent("invalid_client", false, refreshTokenRow.getUserId(), refreshClientId, PRINCIPAL_TYPE_USER, null, request, "invalid_client_or_grant");
            return ResponseMessageUtils.makeResponse(false, 401, messageService.message("Invalid client.", false));
        }

        Long userId = refreshTokenRow.getUserId();
        User user = userId != null ? authMapper.findAuthUserById(userId) : null;
        if (user == null) {
            auditAuthEvent("refresh_failure", false, userId, refreshClientId, PRINCIPAL_TYPE_USER, null, request, "user_not_found");
            return ResponseMessageUtils.makeResponse(false, 401, messageService.message("Invalid or expired token", false));
        }
        if (user.getIsActive() != null && user.getIsActive() != 1) {
            auditAuthEvent("refresh_failure", false, userId, refreshClientId, PRINCIPAL_TYPE_USER, null, request, "user_inactive");
            return ResponseMessageUtils.makeResponse(false, 403, messageService.message("Forbidden", false));
        }

        revokeCurrentAccessTokenIfPresent(request);

        List<String> roles = buildRolesForUser(user);
        Long hospitalId = refreshTokenRow.getHospitalId();
        if (hospitalId == null) {
            auditAuthEvent("refresh_failure", false, userId, refreshClientId, PRINCIPAL_TYPE_USER, null, request, "no_hospital");
            return ResponseMessageUtils.makeResponse(false, 403, messageService.message("Forbidden", false));
        }

        String hospitalCode = authMapper.findHospitalCodeByHospitalId(hospitalId);
        Long permissionVersion = resolvePermissionVersion(user.getId());
        String grantedScope = resolveDefaultScope(clientRow);
        String rolesCsv = String.join(",", roles);
        long accessTokenLifetimeMs = resolveAccessTokenLifetimeMs(clientRow);
        Long refreshTokenLifetimeMs = resolveRefreshTokenLifetimeMs(clientRow);

        // Rotate refresh token on every refresh to reduce replay risk.
        refreshTokenService.revoke(refreshTokenRow.getId(), "ROTATED");

        AccessTokenResponse accessTokenResponse = jwtTokenService.issueUserAccessToken(
                user.getId(), user.getUsername(), refreshClientId, hospitalId, hospitalCode,
                grantedScope, permissionVersion, rolesCsv, accessTokenLifetimeMs);
        List<AccessTokenResponse> tokens = new ArrayList<>();
        tokens.add(accessTokenResponse);
        replaceWithOpaqueRefreshToken(
                tokens,
                userId,
                hospitalId,
                refreshClientId,
                refreshTokenRow.getClientName(),
                request,
                refreshTokenRow.getId(),
                refreshTokenLifetimeMs
        );

        String tokenJti = extractAccessTokenJti(tokens);
        stripBrowserTokensFromResponse(tokens);
        setNoStoreHeaders(response);

        auditAuthEvent("refresh_success", true, userId, refreshClientId, PRINCIPAL_TYPE_USER, tokenJti, request, "ok");

        BaseResult result = new BaseResult();
        result.setData(tokens);
        return ResponseMessageUtils.makeResponse(true, result);
    }

    private int toSecondsFromMillis(long durationMs) {
        if (durationMs <= 0) {
            return 0;
        }
        long seconds = durationMs / 1000L;
        if (seconds > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.max(seconds, 0);
    }

    private void replaceWithOpaqueRefreshToken(List<AccessTokenResponse> tokens,
                                               Long userId,
                                               Long hospitalId,
                                               String clientId,
                                               String clientName,
                                               HttpServletRequest request,
                                               Long rotatedFromId,
                                               Long refreshTokenLifetimeMs) {
        if (tokens == null || tokens.isEmpty() || tokens.getFirst() == null) {
            return;
        }
        String rawRefresh = refreshTokenService.issue(
                userId,
                hospitalId,
                clientId,
                clientName,
                rotatedFromId,
                RequestClientInfoHelper.resolveClientIp(request),
                RequestClientInfoHelper.formatUserAgent(request),
                refreshTokenLifetimeMs
        );
        AccessTokenResponse tokenResponse = tokens.getFirst();
        tokenResponse.setRefreshToken(rawRefresh);

        long resolvedRefreshMs = refreshTokenLifetimeMs != null && refreshTokenLifetimeMs > 0
                ? refreshTokenLifetimeMs
                : refreshTokenExpirationMs;
        tokenResponse.setRefreshExpiresIn((long) toSecondsFromMillis(resolvedRefreshMs));
    }

    private Long resolveHospitalIdForUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return authMapper.findDefaultHospitalIdByUserId(userId);
    }

    private Long resolvePermissionVersion(Long userId) {
        if (userId == null) {
            return 1L;
        }
        Long version = authMapper.findPermissionVersionByUserId(userId);
        return version == null ? 1L : version;
    }

    private void revokeCurrentAccessTokenIfPresent(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String jti = jwtAuth.getToken().getId();
            Long userId = null;
            String clientId = jwtAuth.getToken().getClaimAsString("clientId");
            String principalType = jwtAuth.getToken().getClaimAsString("principalType");
            if (jwtAuth.getDetails() instanceof CurrentUserPrincipal p) {
                userId = p.userId();
            }
            if (jti != null && !jti.isBlank()) {
                Instant exp = jwtAuth.getToken().getExpiresAt();
                LocalDateTime expiresAt = exp != null
                        ? LocalDateTime.ofInstant(exp, ZoneOffset.UTC)
                        : LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15);
                revokedTokenMapper.revokeToken(jti, userId, expiresAt);
                auditAuthEvent("access_token_revoked", true, userId, clientId, principalType, jti, request, "logout_or_rotation");
            }
        }
    }

    private void stripBrowserTokensFromResponse(List<AccessTokenResponse> tokens) {
        if (tokens == null) {
            return;
        }
        for (AccessTokenResponse token : tokens) {
            if (token != null) {
                if (!returnAccessTokenInBody) {
                    token.setAccessToken(null);
                }
                if (!returnRefreshTokenInBody) {
                    token.setRefreshToken(null);
                    token.setRefreshExpiresIn(null);
                }
            }
        }
    }

    private String resolveRefreshToken(RefreshTokenRequest refreshTokenRequest, HttpServletRequest request) {
        if (allowRefreshTokenInBody && refreshTokenRequest != null) {
            String token = refreshTokenRequest.getRefreshToken();
            if (token != null) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty() && !AuthTokenHelper.isNullLike(trimmed)) {
                    return decryptOrOriginal(trimmed);
                }
            }
        }
        return null;
    }

    private String decryptOrOriginal(String token) {
        String decrypted = refreshTokenCryptoService.decrypt(token);
        return decrypted != null && !decrypted.isBlank() ? decrypted : token;
    }

    private List<String> buildRolesForUser(User user) {
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        roles.add("ROLE_USER");

        List<UserGroupList> groups = userMapper.getOneUserGroupList(user.getId());
        if (groups != null) {
            for (UserGroupList group : groups) {
                String normalized = AuthorityUtils.normalizeRole(group != null ? group.getName() : null);
                if (normalized != null) {
                    roles.add(normalized);
                }
            }
        }
        return roles.stream().toList();
    }

    private void revokeRefreshTokenIfPresent(RefreshTokenRequest refreshTokenRequest, HttpServletRequest request) {
        String refreshToken = resolveRefreshToken(refreshTokenRequest, request);
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        RefreshTokenRow row = refreshTokenService.validate(refreshToken);
        if (row != null) {
            refreshTokenService.revoke(row.getId(), "LOGOUT");
        }
    }

    private static void setNoStoreHeaders(HttpServletResponse response) {
        if (response == null) {
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }

    private static Long extractUserId(Authentication authentication) {
        // Helper flow: prepare user id for the current service operation.
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails customUserDetails) {
            return customUserDetails.id();
        }
        if (authentication.getDetails() instanceof CurrentUserPrincipal currentUserPrincipal) {
            return currentUserPrincipal.userId();
        }
        return null;
    }

    private static String resolveLoginIdentifier(LoginRequest loginRequest) {
        if (loginRequest == null) {
            return "";
        }
        return trimToEmpty(loginRequest.getUsername());
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** {@inheritDoc} */
    @Override
    public User findUserByUsername(String username) {
        // Query flow: load user by username and return API response.
        return authMapper.findUserDetails(username);
    }

    /** {@inheritDoc} */
    @Override
    public User findUserByEmail(String email) {
        // Query flow: load user by email and return API response.
        return authMapper.findOneByEmail(email);
    }

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> handleClientCredentials(ClientCredentialsRequest ccRequest, HttpServletRequest httpServletRequest) {
        if (ccRequest == null) {
            return ResponseMessageUtils.makeResponse(false, 400, messageService.message("Invalid request.", false));
        }

        String clientId = trimToNull(ccRequest.getClientId());
        OAuth2ClientRow clientRow = validateOAuthClientForGrant(clientId, "client_credentials", true);
        if (clientRow == null) {
            auditAuthEvent("client_credentials_failure", false, null, clientId, PRINCIPAL_TYPE_CLIENT, null, httpServletRequest, "invalid_client_or_grant");
            return ResponseMessageUtils.makeResponse(false, 401, messageService.message("Invalid client.", false));
        }

        String providedSecret = ccRequest.getClientSecret();
        String clientSecretHash = trimToNull(clientRow.getClientSecretHash());
        if (providedSecret == null || clientSecretHash == null || !passwordEncoder.matches(providedSecret, clientSecretHash)) {
            auditAuthEvent("client_credentials_failure", false, null, clientId, PRINCIPAL_TYPE_CLIENT, null, httpServletRequest, "invalid_client_secret");
            return ResponseMessageUtils.makeResponse(false, 401, messageService.message("Invalid client credentials.", false));
        }

        ScopeResolution scopeResolution = resolveGrantedScope(ccRequest.getScope(), clientRow);
        if (!scopeResolution.valid()) {
            auditAuthEvent("invalid_scope", false, null, clientId, PRINCIPAL_TYPE_CLIENT, null, httpServletRequest, scopeResolution.reasonCode());
            return ResponseMessageUtils.makeResponse(false, 403, messageService.message("Invalid scope.", false));
        }

        long accessTokenLifetimeMs = resolveAccessTokenLifetimeMs(clientRow);
        AccessTokenResponse tokenResponse = jwtTokenService.issueClientAccessToken(
                clientId,
                scopeResolution.grantedScope(),
                accessTokenLifetimeMs,
                clientRow.getDicomServerId()
        );
        String tokenJti = jwtTokenService.extractJti(tokenResponse.getAccessToken());
        auditAuthEvent("client_credentials_success", true, null, clientId, PRINCIPAL_TYPE_CLIENT, tokenJti, httpServletRequest, "ok");

        BaseResult result = new BaseResult();
        result.setData(List.of(tokenResponse));
        return ResponseMessageUtils.makeResponse(true, result);
    }

    private OAuth2ClientRow validateOAuthClientForGrant(String clientId, String grantType, boolean requireConfidential) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }

        OAuth2ClientRow clientRow = oauth2ClientMapper.findByClientId(clientId);
        if (clientRow == null || !Boolean.TRUE.equals(clientRow.getIsActive())) {
            return null;
        }
        if (!clientRow.allowsGrantType(grantType)) {
            return null;
        }
        if (requireConfidential && !clientRow.isConfidential()) {
            return null;
        }

        return clientRow;
    }

    private long resolveAccessTokenLifetimeMs(OAuth2ClientRow clientRow) {
        if (clientRow != null && clientRow.getAccessTokenLifetimeMs() != null && clientRow.getAccessTokenLifetimeMs() > 0) {
            return clientRow.getAccessTokenLifetimeMs();
        }
        return 0L;
    }

    private Long resolveRefreshTokenLifetimeMs(OAuth2ClientRow clientRow) {
        if (clientRow != null && clientRow.getRefreshTokenLifetimeMs() != null && clientRow.getRefreshTokenLifetimeMs() > 0) {
            return clientRow.getRefreshTokenLifetimeMs();
        }
        return refreshTokenExpirationMs;
    }

    private String resolveDefaultScope(OAuth2ClientRow clientRow) {
        if (clientRow == null || clientRow.getAllowedScopes() == null || clientRow.getAllowedScopes().isBlank()) {
            return "pacs.api";
        }
        String normalizedAllowed = String.join(" ", clientRow.getAllowedScopes().trim().split("\\s+")).trim();
        if (normalizedAllowed.isBlank()) {
            return "pacs.api";
        }
        return normalizedAllowed;
    }

    private ScopeResolution resolveGrantedScope(String requestedScope, OAuth2ClientRow client) {
        if (client == null || client.getAllowedScopes() == null || client.getAllowedScopes().isBlank()) {
            return new ScopeResolution(true, "pacs.api", null);
        }

        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        for (String value : client.getAllowedScopes().trim().split("\\s+")) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                allowed.add(trimmed);
            }
        }
        if (allowed.isEmpty()) {
            return new ScopeResolution(true, "pacs.api", null);
        }

        if (requestedScope == null || requestedScope.isBlank()) {
            return new ScopeResolution(true, String.join(" ", allowed), null);
        }

        LinkedHashSet<String> requested = new LinkedHashSet<>();
        for (String value : requestedScope.trim().split("\\s+")) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                requested.add(trimmed);
            }
        }
        if (requested.isEmpty()) {
            return new ScopeResolution(false, null, "empty_requested_scope");
        }

        for (String scope : requested) {
            if (!allowed.contains(scope)) {
                return new ScopeResolution(false, null, "scope_not_allowed");
            }
        }

        return new ScopeResolution(true, String.join(" ", requested), null);
    }

    private String extractAccessTokenJti(List<AccessTokenResponse> tokens) {
        if (tokens == null || tokens.isEmpty() || tokens.getFirst() == null) {
            return null;
        }
        return jwtTokenService.extractJti(tokens.getFirst().getAccessToken());
    }

    private void auditAuthEvent(String event,
                                boolean success,
                                Long userId,
                                String clientId,
                                String principalType,
                                String jti,
                                HttpServletRequest request,
                                String reasonCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("success", success);
        payload.put("reasonCode", reasonCode);
        if (userId != null) {
            payload.put("userId", userId);
        }
        if (clientId != null) {
            payload.put("clientId", clientId);
        }
        if (principalType != null) {
            payload.put("principalType", principalType);
        }
        if (jti != null) {
            payload.put("jti", jti);
        }
        if (request != null) {
            payload.put("ip", RequestClientInfoHelper.resolveClientIp(request));
            payload.put("userAgent", RequestClientInfoHelper.formatUserAgent(request));
        }
        logger.info("AUTH_AUDIT {}", payload);
    }

    private void normalizeLoginRequest(LoginRequest loginRequest) {
        loginRequest.setClientId(trimToNull(loginRequest.getClientId()));
        loginRequest.setClientName(trimToNull(loginRequest.getClientName()));
        loginRequest.setUsername(trimToNull(loginRequest.getUsername()));
        loginRequest.setPassword(trimToNull(loginRequest.getPassword()));
    }

    private record ScopeResolution(boolean valid, String grantedScope, String reasonCode) {
    }
}
