package com.ut.emrPacs.authentication.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PublicViewerAttemptGuard {

    @Value("${app.security.public-viewer.link-window-seconds:900}")
    private int windowSeconds;

    @Value("${app.security.public-viewer.link-max-failures:10}")
    private int maxFailures;

    private Cache<String, AtomicInteger> failures;

    @PostConstruct
    void initialize() {
        failures = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(Math.max(60, windowSeconds), TimeUnit.SECONDS)
                .build();
    }

    public boolean isBlocked(String hospitalKey, String worklistKey) {
        AtomicInteger count = cache().getIfPresent(linkFingerprint(hospitalKey, worklistKey));
        return count != null && count.get() >= Math.max(1, maxFailures);
    }

    public void recordFailure(String hospitalKey, String worklistKey) {
        cache().get(linkFingerprint(hospitalKey, worklistKey), ignored -> new AtomicInteger())
                .incrementAndGet();
    }

    public void clear(String hospitalKey, String worklistKey) {
        cache().invalidate(linkFingerprint(hospitalKey, worklistKey));
    }

    private Cache<String, AtomicInteger> cache() {
        if (failures == null) {
            initialize();
        }
        return failures;
    }

    private static String linkFingerprint(String hospitalKey, String worklistKey) {
        String value = normalize(hospitalKey) + "|" + normalize(worklistKey);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to protect public viewer attempts.", error);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
