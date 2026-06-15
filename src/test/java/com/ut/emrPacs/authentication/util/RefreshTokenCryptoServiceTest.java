package com.ut.emrPacs.authentication.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RefreshTokenCryptoServiceTest {

    private static final String TEST_KEY_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String TEST_KEY_RAW = "ProdRefreshKey_2026_Strong_OnlyForTest_9xA2!";

    @Test
    void shouldPassThroughTokenWhenEncryptionDisabledOutsideProd() {
        RefreshTokenCryptoService service = new RefreshTokenCryptoService("", "local");
        String token = "plain-refresh-token";

        assertEquals(token, service.encrypt(token));
        assertEquals(token, service.decrypt(token));
    }

    @Test
    void shouldPassThroughTokenWhenEncryptionDisabledInProdBearerMode() {
        RefreshTokenCryptoService service = new RefreshTokenCryptoService("", "prod");
        String token = "plain-refresh-token";

        assertEquals(token, service.encrypt(token));
        assertEquals(token, service.decrypt(token));
    }

    @Test
    void shouldEncryptAndDecryptWhenKeyConfigured() {
        RefreshTokenCryptoService service = new RefreshTokenCryptoService(TEST_KEY_BASE64, "prod");
        String token = "refresh-token-value";

        String encrypted = service.encrypt(token);
        assertNotNull(encrypted);
        assertNotEquals(token, encrypted);
        assertEquals(token, service.decrypt(encrypted));
    }

    @Test
    void shouldEncryptAndDecryptWhenRawKeyConfigured() {
        RefreshTokenCryptoService service = new RefreshTokenCryptoService(TEST_KEY_RAW, "qa");
        String token = "refresh-token-value";

        String encrypted = service.encrypt(token);
        assertNotNull(encrypted);
        assertNotEquals(token, encrypted);
        assertEquals(token, service.decrypt(encrypted));
    }
}
