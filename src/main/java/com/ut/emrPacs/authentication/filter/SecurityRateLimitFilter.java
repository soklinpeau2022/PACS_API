package com.ut.emrPacs.authentication.filter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.http.RequestClientInfoHelper;
import com.ut.emrPacs.helper.security.SecurityAuditLogger;
import com.ut.emrPacs.model.base.ResponseMessageUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@NullMarked
public class SecurityRateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityRateLimitFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> LOGIN_ENDPOINTS = Set.of(
            ApiConstants.Auth.LOGIN_FULL_PATH
    );
    private static final String PUBLIC_VIEWER_ENDPOINT =
            ApiConstants.PacsResultApi.BASE_PATH + ApiConstants.PacsResultApi.PUBLIC_VIEWER_AUTHORIZE_PATH;

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.security.rate-limit.auth-window-seconds:60}")
    private int authWindowSeconds;

    @Value("${app.security.rate-limit.auth-max-requests:60}")
    private int authMaxRequests;

    @Value("${app.security.rate-limit.login-window-seconds:60}")
    private int loginWindowSeconds;

    @Value("${app.security.rate-limit.login-max-requests:5}")
    private int loginMaxRequests;

    @Value("${app.security.rate-limit.public-viewer-window-seconds:300}")
    private int publicViewerWindowSeconds;

    @Value("${app.security.rate-limit.public-viewer-max-requests:5}")
    private int publicViewerMaxRequests;

    // Initialised in @PostConstruct once @Value fields are injected.
    private Cache<String, SlidingWindowCounter> counters;

    @PostConstruct
    void initCache() {
        long ttlSeconds = Math.max(Math.max(authWindowSeconds, loginWindowSeconds), publicViewerWindowSeconds) * 2L;
        counters = Caffeine.newBuilder()
                .maximumSize(20_000)
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String normalizedPath = normalizePath(request);
        String clientIp = RequestClientInfoHelper.resolveClientIp(request);
        if (clientIp.isBlank()) {
            clientIp = "unknown";
        }

        if (!normalizedPath.startsWith("/auth/") && !PUBLIC_VIEWER_ENDPOINT.equals(normalizedPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        LimitRule limitRule = resolveLimitRule(normalizedPath);
        if (limitRule.maxRequests <= 0 || limitRule.windowSeconds <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = limitRule.bucket + ":" + clientIp;
        SlidingWindowCounter counter = getCounters().get(key, unused -> new SlidingWindowCounter());
        if (!counter.tryAcquire(limitRule.windowSeconds, limitRule.maxRequests)) {
            LOGGER.warn("Rate limit exceeded: bucket={}, ip={}, path={}", limitRule.bucket, clientIp, normalizedPath);
            SecurityAuditLogger.logBlocked(LOGGER, request, "rate_limit", limitRule.bucket, normalizedPath);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String json = OBJECT_MAPPER.writeValueAsString(
                    ResponseMessageUtils.makeResponse(false, 429, "TOO_MANY_REQUESTS", "Too many requests. Please try again later.")
            );
            response.getWriter().write(json);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Cache<String, SlidingWindowCounter> getCounters() {
        Cache<String, SlidingWindowCounter> current = counters;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (counters == null) {
                initCache();
            }
            return counters;
        }
    }

    private String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private LimitRule resolveLimitRule(String path) {
        if (PUBLIC_VIEWER_ENDPOINT.equals(path)) {
            return new LimitRule("public-viewer", publicViewerWindowSeconds, publicViewerMaxRequests);
        }
        if (LOGIN_ENDPOINTS.contains(path)) {
            return new LimitRule("login", loginWindowSeconds, loginMaxRequests);
        }
        return new LimitRule("auth", authWindowSeconds, authMaxRequests);
    }

    private record LimitRule(String bucket, int windowSeconds, int maxRequests) {}

    /**
     * Sliding window rate limiter using a timestamp deque.
     *
     * <p>Maintains a deque of request timestamps for the last window period.
     * On each {@link #tryAcquire}: prune timestamps older than the window,
     * then accept if count is within limit. This eliminates the fixed-window
     * boundary burst attack (where 5 req at end-of-window + 5 req at
     * start-of-next-window = 10 effective requests).</p>
     */
    private static final class SlidingWindowCounter {
        private final Deque<Long> timestamps = new ArrayDeque<>();

        private synchronized boolean tryAcquire(int windowSeconds, int maxRequests) {
            long now = System.currentTimeMillis();
            long windowStart = now - Math.max(1, windowSeconds) * 1000L;

            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
