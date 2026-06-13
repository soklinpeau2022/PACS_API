package com.ut.emrPacs.authentication.util;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ut.emrPacs.model.dto.response.authentication.token.AccessTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues RS256-signed JWT access tokens for both user login and client credentials flows.
 *
 * Claims layout:
 * - iss, aud, sub, principalType, username (USER only), clientId, tokenUse, scope, jti, iat, exp
 * - hospitalId, hospitalCode, permissionVersion, roles (USER only — consumed by ModulePermissionFilter)
 */
@Service
public class JwtTokenService {

    private final RsaKeyLoader rsaKeyLoader;

    @Value("${security.jwt.issuer:udaya_pacs_api}")
    private String issuer;

    @Value("${security.jwt.audience:pacs-web}")
    private String audience;

    @Value("${security.jwt.access-token-ms:900000}")
    private long defaultAccessTokenMs;

    @Value("${security.jwt.key-id:}")
    private String keyId;

    public JwtTokenService(RsaKeyLoader rsaKeyLoader) {
        this.rsaKeyLoader = rsaKeyLoader;
    }

    /**
     * Issues a JWT access token for an authenticated user (password_login grant).
     */
    public AccessTokenResponse issueUserAccessToken(
            Long userId,
            String username,
            String clientId,
            Long hospitalId,
            String hospitalCode,
            String scope,
            Long permissionVersion,
            String roles,
            long lifetimeMs
    ) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant exp = now.plusMillis(lifetimeMs > 0 ? lifetimeMs : defaultAccessTokenMs);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(String.valueOf(userId))
                .claim("principalType", "USER")
                .claim("username", username)
                .claim("clientId", clientId)
                .claim("tokenUse", "access")
                .claim("scope", scope)
                .claim("hospitalId", hospitalId)
                .claim("hospitalCode", hospitalCode)
                .claim("permissionVersion", permissionVersion)
                .claim("roles", roles)
                .jwtID(jti)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .build();

        String token = sign(claims);
        long expiresInSeconds = exp.getEpochSecond() - now.getEpochSecond();
        return buildResponse(token, expiresInSeconds, scope);
    }

    /**
     * Issues a JWT access token for a machine client (client_credentials grant).
     */
    public AccessTokenResponse issueClientAccessToken(
            String clientId,
            String scope,
            long lifetimeMs
    ) {
        return issueClientAccessToken(clientId, scope, lifetimeMs, null);
    }

    /**
     * Issues a JWT access token for a machine client and optionally binds it
     * to one DICOM server.
     */
    public AccessTokenResponse issueClientAccessToken(
            String clientId,
            String scope,
            long lifetimeMs,
            Long dicomServerId
    ) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant exp = now.plusMillis(lifetimeMs > 0 ? lifetimeMs : defaultAccessTokenMs);

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(clientId)
                .claim("principalType", "CLIENT")
                .claim("clientId", clientId)
                .claim("tokenUse", "access")
                .claim("scope", scope)
                .jwtID(jti)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp));

        if (dicomServerId != null && dicomServerId > 0) {
            claimsBuilder.claim("dicomServerId", dicomServerId);
        }

        JWTClaimsSet claims = claimsBuilder.build();

        String token = sign(claims);
        long expiresInSeconds = exp.getEpochSecond() - now.getEpochSecond();
        return buildResponse(token, expiresInSeconds, scope);
    }

    /**
     * Issues a short-lived viewer token for the DICOMweb gateway. The browser
     * only receives this scoped token, never DicomServer credentials.
     */
    public AccessTokenResponse issueViewerDicomwebToken(
            Long hospitalId,
            Long worklistId,
            String studyInstanceUid,
            long lifetimeMs
    ) {
        return issueViewerDicomwebToken(hospitalId, worklistId, null, studyInstanceUid, lifetimeMs);
    }

    public AccessTokenResponse issueViewerDicomwebToken(
            Long hospitalId,
            Long worklistId,
            Long studyId,
            String studyInstanceUid,
            long lifetimeMs
    ) {
        String scope = "pacs.viewer.dicomweb";
        String clientId = "pacs-viewer-dicomweb";
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant exp = now.plusMillis(lifetimeMs > 0 ? lifetimeMs : defaultAccessTokenMs);

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(clientId)
                .claim("principalType", "CLIENT")
                .claim("clientId", clientId)
                .claim("tokenUse", "access")
                .claim("scope", scope)
                .claim("hospitalId", hospitalId)
                .claim("studyInstanceUid", studyInstanceUid)
                .jwtID(jti)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp));

        if (worklistId != null && worklistId > 0) {
            claimsBuilder.claim("worklistId", worklistId);
        }
        if (studyId != null && studyId > 0) {
            claimsBuilder.claim("studyId", studyId);
        }

        String token = sign(claimsBuilder.build());
        long expiresInSeconds = exp.getEpochSecond() - now.getEpochSecond();
        return buildResponse(token, expiresInSeconds, scope);
    }

    /**
     * Issues a signed viewer API key for OHIF. The key is not an DicomServer
     * credential; it is scoped to one hospital/worklist/study and one access
     * mode so DICOMweb and PACS Result can authorize the same viewer session.
     */
    public AccessTokenResponse issueViewerApiKey(
            Long hospitalId,
            Long worklistId,
            Long studyId,
            Long modalityId,
            String studyInstanceUid,
            Long userId,
            String username,
            String accessMode,
            long lifetimeMs
    ) {
        String scope = "pacs.viewer.api";
        String clientId = "pacs-viewer-api";
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant exp = now.plusMillis(lifetimeMs > 0 ? lifetimeMs : defaultAccessTokenMs);

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(clientId)
                .claim("principalType", "CLIENT")
                .claim("clientId", clientId)
                .claim("tokenUse", "access")
                .claim("scope", scope)
                .claim("hospitalId", hospitalId)
                .claim("accessMode", accessMode)
                .jwtID(jti)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp));

        if (worklistId != null && worklistId > 0) {
            claimsBuilder.claim("worklistId", worklistId);
        }
        if (studyId != null && studyId > 0) {
            claimsBuilder.claim("studyId", studyId);
        }
        if (modalityId != null && modalityId > 0) {
            claimsBuilder.claim("modalityId", modalityId);
        }
        if (studyInstanceUid != null && !studyInstanceUid.isBlank()) {
            claimsBuilder.claim("studyInstanceUid", studyInstanceUid.trim());
        }
        if (userId != null && userId > 0) {
            claimsBuilder.claim("userId", userId);
        }
        if (username != null && !username.isBlank()) {
            claimsBuilder.claim("username", username.trim());
        }

        String token = sign(claimsBuilder.build());
        long expiresInSeconds = exp.getEpochSecond() - now.getEpochSecond();
        return buildResponse(token, expiresInSeconds, scope);
    }

    private String sign(JWTClaimsSet claims) {
        try {
            RSASSASigner signer = new RSASSASigner(rsaKeyLoader.getPrivateKey());
            JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256);
            if (keyId != null && !keyId.isBlank()) {
                headerBuilder.keyID(keyId.trim());
            }
            SignedJWT jwt = new SignedJWT(headerBuilder.build(), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT access token", e);
        }
    }

    public String extractJti(String jwtToken) {
        if (jwtToken == null || jwtToken.isBlank()) {
            return null;
        }
        try {
            SignedJWT parsed = SignedJWT.parse(jwtToken);
            JWTClaimsSet claims = parsed.getJWTClaimsSet();
            return claims != null ? claims.getJWTID() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private AccessTokenResponse buildResponse(String token, long expiresInSeconds, String scope) {
        return new AccessTokenResponse("Bearer", token, null, expiresInSeconds, scope);
    }
}
