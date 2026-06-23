package com.ut.emrPacs.helper;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TelegramHelperTest {

    @Test
    void redactsTelegramBotTokenFromErrorMessages() {
        String redacted = ReflectionTestUtils.invokeMethod(
                TelegramHelper.class,
                "redactSensitiveTelegramData",
                "429 Too Many Requests on https://api.telegram.org/botSECRET_TOKEN/sendMessage?token=OTHER_SECRET"
        );

        assertTrue(redacted.contains("bot<redacted>/sendMessage"));
        assertTrue(redacted.contains("token=<redacted>"));
        assertFalse(redacted.contains("SECRET_TOKEN"));
        assertFalse(redacted.contains("OTHER_SECRET"));
    }

    @Test
    void missingTelegramConfigurationIsIgnored() {
        TelegramHelper telegramHelper = new TelegramHelper("", mock(RestTemplate.class));

        telegramHelper.sendTextMessage("message", "chat-1");
        telegramHelper.sendTextMessage("message", "");
    }
}
