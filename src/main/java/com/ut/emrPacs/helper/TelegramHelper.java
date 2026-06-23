package com.ut.emrPacs.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Utility class for sending messages and documents to Telegram chats via bot.
 * Uses Spring's RestTemplate for robust and testable HTTP interactions.
 */
@Slf4j
@Component
public class TelegramHelper {

    private final RestTemplate restTemplate;
    private final String apiToken;

    @Autowired
    public TelegramHelper(
            @Value("${telegram.api.token}") String apiToken,
            RestTemplate restTemplate
    ) {
        this.apiToken = apiToken;
        this.restTemplate = restTemplate;
    }

    /**
     * Sends a plain text (HTML) message to the specified Telegram chat.
     *
     * @param message The message content (supports Telegram HTML)
     * @param chatId  The Telegram chat ID (user, group, or channel)
     */
    public void sendTextMessage(String message, String chatId) {
        if (apiToken == null || apiToken.isBlank()) {
            log.debug("Skip Telegram send: telegram.api.token is empty.");
            return;
        }
        if (chatId == null || chatId.isBlank()) {
            log.debug("Skip Telegram send: chatId is empty.");
            return;
        }
        try {
            String url = "https://api.telegram.org/bot{token}/sendMessage";
            log.debug("Sending Telegram message to configured chat.");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            ResponseEntity<String> response = sendWithHtml(url, headers, chatId, message);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Telegram text message sent to chat {} successfully.", chatId);
            } else {
                log.error("Failed to send Telegram message. Status: {} Response: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.warn("Telegram message send failed: {}", summarizeError(e));
        }
    }

    private ResponseEntity<String> sendWithHtml(String url, HttpHeaders headers, String chatId, String message) {
        String safeHtml = message != null ? message : "";
        try {
            MultiValueMap<String, String> htmlForm = new LinkedMultiValueMap<>();
            htmlForm.add("chat_id", chatId);
            htmlForm.add("text", safeHtml);
            htmlForm.add("parse_mode", "HTML");

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(htmlForm, headers);
            return restTemplate.postForEntity(url, requestEntity, String.class, apiToken);
        } catch (Exception htmlError) {
            log.warn("Telegram HTML send failed. Retrying as plain text. Error: {}", summarizeError(htmlError));
            MultiValueMap<String, String> plainForm = new LinkedMultiValueMap<>();
            plainForm.add("chat_id", chatId);
            plainForm.add("text", stripHtml(message != null ? message : ""));
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(plainForm, headers);
            return restTemplate.postForEntity(url, requestEntity, String.class, apiToken);
        }
    }

    private static String summarizeError(Exception error) {
        if (error == null) {
            return "unknown";
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return error.getClass().getSimpleName() + ": " + redactSensitiveTelegramData(message);
    }

    private static String redactSensitiveTelegramData(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replaceAll("(?i)bot[^/\\s\"']+/sendMessage", "bot<redacted>/sendMessage")
                .replaceAll("(?i)(token=)[^&\\s\"']+", "$1<redacted>");
    }

    private String stripHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p\\s*>", "\n")
                .replaceAll("(?is)<[^>]+>", "");
    }

}
