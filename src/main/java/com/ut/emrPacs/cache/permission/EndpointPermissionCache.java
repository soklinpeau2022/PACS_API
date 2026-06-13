package com.ut.emrPacs.cache.permission;

import com.github.benmanes.caffeine.cache.Cache;
import com.ut.emrPacs.mapper.permission.EndpointPermissionMapper;
import com.ut.emrPacs.model.permission.EndpointPermissionRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service("endpointPermissionCacheService")
public class EndpointPermissionCache {

    private static final EndpointPermissionRule NO_RULE = buildNoRule();

    @Autowired
    @Qualifier("endpointPermissionCache")
    private Cache<String, EndpointPermissionRule> cache;

    @Autowired
    private EndpointPermissionMapper endpointPermissionMapper;

    @Value("${security.permission.endpoint-rules-refresh-seconds:60}")
    private long endpointRulesRefreshSeconds;

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final Object rulesLock = new Object();
    private volatile List<EndpointPermissionRule> rulesSnapshot = List.of();
    private volatile long rulesSnapshotLoadedAtMs = 0L;

    public String resolvePermissionCode(String method, String normalizedPath) {
        EndpointPermissionRule rule = resolveRule(method, normalizedPath);
        return rule != null ? rule.getPermissionCode() : null;
    }

    public EndpointPermissionRule resolveRule(String method, String normalizedPath) {
        if (method == null || normalizedPath == null) {
            return null;
        }

        String methodUpper = method.toUpperCase();
        if (HttpMethod.OPTIONS.matches(methodUpper)) {
            return null;
        }

        String key = methodUpper + " " + normalizedPath;
        EndpointPermissionRule cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached == NO_RULE ? null : cached;
        }

        List<EndpointPermissionRule> rules = getAllRules();
        EndpointPermissionRule found = null;
        for (EndpointPermissionRule rule : rules) {
            if (rule == null) continue;
            if (!methodUpper.equalsIgnoreCase(rule.getHttpMethod())) continue;
            if (rule.getEndpointPattern() == null) continue;

            String pattern = normalizePattern(rule.getEndpointPattern());
            if (matcher.match(pattern, normalizedPath)) {
                found = rule;
                break;
            }
        }

        cache.put(key, found == null ? NO_RULE : found);
        return found;
    }

    public void clear() {
        cache.invalidateAll();
        synchronized (rulesLock) {
            rulesSnapshot = List.of();
            rulesSnapshotLoadedAtMs = 0L;
        }
    }

    private List<EndpointPermissionRule> getAllRules() {
        long now = System.currentTimeMillis();
        long refreshMs = Math.max(5L, endpointRulesRefreshSeconds) * 1000L;

        if (rulesSnapshotLoadedAtMs > 0L && (now - rulesSnapshotLoadedAtMs) < refreshMs) {
            return rulesSnapshot;
        }

        synchronized (rulesLock) {
            now = System.currentTimeMillis();
            if (rulesSnapshotLoadedAtMs > 0L && (now - rulesSnapshotLoadedAtMs) < refreshMs) {
                return rulesSnapshot;
            }

            List<EndpointPermissionRule> rules = endpointPermissionMapper.listActiveRules();
            List<EndpointPermissionRule> updated = rules == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(rules));

            rulesSnapshot = updated;
            rulesSnapshotLoadedAtMs = now;
            return rulesSnapshot;
        }
    }

    private static String normalizePattern(String pattern) {
        String p = pattern.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (p.endsWith("/*")) {
            p = p.substring(0, p.length() - 1) + "**";
        }
        return p;
    }

    private static EndpointPermissionRule buildNoRule() {
        EndpointPermissionRule noRule = new EndpointPermissionRule();
        noRule.setHttpMethod("_");
        noRule.setEndpointPattern("_");
        noRule.setPermissionCode("");
        noRule.setRequiredScope("");
        return noRule;
    }
}
