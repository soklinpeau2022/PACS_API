package com.ut.emrPacs.cache.permission;

import com.github.benmanes.caffeine.cache.Cache;
import com.ut.emrPacs.mapper.permission.PermissionMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Collection;

@Service
public class PermissionCacheService {

    private final Cache<String, Set<String>> cache;
    private final PermissionMapper permissionMapper;

    public PermissionCacheService(@Qualifier("permissionCodeCache") Cache<String, Set<String>> cache,
                                  PermissionMapper permissionMapper) {
        this.cache = cache;
        this.permissionMapper = permissionMapper;
    }

    public Set<String> getPermissionCodes(Long userId, Long hospitalId, Long permissionVersion) {
        if (userId == null || hospitalId == null) {
            return Set.of();
        }
        String key = userId + ":" + hospitalId + ":" + (permissionVersion == null ? 0L : permissionVersion);
        Set<String> existing = cache.getIfPresent(key);
        if (existing != null) {
            return existing;
        }

        List<String> raw = permissionMapper.listPermissionCodes(userId, hospitalId);
        Set<String> values = raw == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(raw));
        cache.put(key, values);
        return values;
    }

    public void invalidateByUserAndHospital(Long userId, Long hospitalId) {
        if (userId == null || hospitalId == null) {
            return;
        }
        String prefix = userId + ":" + hospitalId + ":";
        cache.asMap().keySet().removeIf(k -> k != null && k.startsWith(prefix));
    }

    public void invalidateByUser(Long userId) {
        if (userId == null) {
            return;
        }
        String prefix = userId + ":";
        cache.asMap().keySet().removeIf(k -> k != null && k.startsWith(prefix));
    }

    public void invalidateByUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds) {
            invalidateByUser(userId);
        }
    }
}
