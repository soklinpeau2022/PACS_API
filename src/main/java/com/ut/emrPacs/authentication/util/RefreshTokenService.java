package com.ut.emrPacs.authentication.util;

import com.ut.emrPacs.mapper.auth.RefreshTokenMapper;
import com.ut.emrPacs.model.auth.RefreshTokenRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private final RefreshTokenMapper refreshTokenMapper;
    private final long refreshTokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenMapper refreshTokenMapper,
                               @Value("${security.jwt.refresh-token-ms:2592000000}") long refreshTokenExpirationMs) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String issue(Long userId,
                        Long hospitalId,
                        String clientId,
                        String clientName,
                        Long rotatedFromId,
                        String ipAddress,
                        String userAgent) {
        return issue(userId, hospitalId, clientId, clientName, rotatedFromId, ipAddress, userAgent, null);
    }

    public String issue(Long userId,
                        Long hospitalId,
                        String clientId,
                        String clientName,
                        Long rotatedFromId,
                        String ipAddress,
                        String userAgent,
                        Long lifetimeMsOverride) {
        String raw = generateSecureToken();
        RefreshTokenRow row = new RefreshTokenRow();
        row.setUserId(userId);
        row.setHospitalId(hospitalId);
        row.setClientId(clientId == null || clientId.isBlank() ? "web" : clientId.trim());
        row.setClientName(clientName);
        row.setRotatedFromId(rotatedFromId);
        row.setTokenHash(sha256(raw));
        long lifetimeMs = lifetimeMsOverride != null && lifetimeMsOverride > 0 ? lifetimeMsOverride : refreshTokenExpirationMs;
        row.setExpiresAt(LocalDateTime.now().plusSeconds(Math.max(lifetimeMs / 1000L, 60L)));
        row.setIpAddress(ipAddress);
        row.setUserAgent(userAgent);
        refreshTokenMapper.insert(row);
        return raw;
    }

    public RefreshTokenRow validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        return refreshTokenMapper.findActiveByTokenHash(sha256(rawToken));
    }

    public void revoke(Long id, String reason) {
        if (id == null) {
            return;
        }
        refreshTokenMapper.revokeById(id, reason == null || reason.isBlank() ? "REVOKED" : reason);
    }

    public void cleanupExpired() {
        refreshTokenMapper.revokeExpired(LocalDateTime.now());
    }

    /** Returns the SHA-256 hex hash of the raw token. Used for reuse-detection lookups. */
    public String hashToken(String rawToken) {
        return sha256(rawToken);
    }

    private String generateSecureToken() {
        byte[] random = new byte[32]; // 256-bit entropy
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
