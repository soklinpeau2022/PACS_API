package com.ut.emrPacs.authentication.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class RefreshTokenCryptoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenCryptoService.class);
    private static final String AES = "AES";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final SecretKey secretKey;
    private final boolean encryptionEnabled;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenCryptoService(
            @Value("${security.jwt.refresh-token-encryption-key:}") String base64Key,
            @Value("${spring.profiles.active:}") String activeProfiles
    ) {
        SecretKey parsedKey = null;
        boolean enabled = false;
        boolean strictProfile = isStrictProfile(activeProfiles);

        if (base64Key != null && !base64Key.trim().isEmpty()) {
            try {
                parsedKey = parseSecretKey(base64Key.trim());
                enabled = parsedKey != null;
            } catch (IllegalArgumentException ex) {
                logEncryptionDisabled("Invalid security.jwt.refresh-token-encryption-key format.", strictProfile);
            }
        } else {
            logEncryptionDisabled("security.jwt.refresh-token-encryption-key is not set.", strictProfile);
        }

        if (!enabled && strictProfile) {
            throw new IllegalStateException("security.jwt.refresh-token-encryption-key must be configured in strict profile (qa/prod).");
        }

        this.secretKey = parsedKey;
        this.encryptionEnabled = enabled;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        if (!encryptionEnabled) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return base64Url(iv) + "." + base64Url(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt refresh token", e);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        if (!encryptionEnabled) {
            return encrypted;
        }
        String[] parts = encrypted.split("\\.", -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            byte[] iv = base64UrlDecode(parts[0]);
            byte[] ciphertext = base64UrlDecode(parts[1]);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static SecretKey parseSecretKey(String configuredValue) {
        // Preferred format: base64:<value>, where decoded length is 16/24/32 bytes.
        if (configuredValue.startsWith("base64:")) {
            byte[] decoded = Base64.getDecoder().decode(configuredValue.substring("base64:".length()).trim());
            if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
                return new SecretKeySpec(decoded, AES);
            }
            throw new IllegalArgumentException("Invalid Base64 key length.");
        }

        // Backward-compatible mode: try plain Base64 first.
        try {
            byte[] decoded = Base64.getDecoder().decode(configuredValue);
            if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
                return new SecretKeySpec(decoded, AES);
            }
        } catch (IllegalArgumentException ignored) {
            // Fallback below.
        }

        // Fallback mode: treat configured value as passphrase and derive a stable 256-bit key.
        return new SecretKeySpec(sha256(configuredValue), AES);
    }

    private static byte[] sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    private static boolean isStrictProfile(String activeProfiles) {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        String[] profiles = activeProfiles.split(",");
        for (String profile : profiles) {
            String normalized = profile.trim().toLowerCase();
            if ("qa".equals(normalized) || "prod".equals(normalized) || "production".equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private void logEncryptionDisabled(String reason, boolean strictProfile) {
        if (strictProfile) {
            LOGGER.warn("{} Refresh token encryption disabled.", reason);
            return;
        }
        LOGGER.info("{} Refresh token encryption disabled for non-production profile.", reason);
    }
}

