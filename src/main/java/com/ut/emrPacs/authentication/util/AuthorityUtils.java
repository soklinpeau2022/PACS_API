package com.ut.emrPacs.authentication.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Locale;

public final class AuthorityUtils {

    private static final java.util.Set<String> ADMIN_ROLES =
            java.util.Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_SYSTEM_ADMIN");

    private AuthorityUtils() {
    }

    /**
     * Returns {@code true} when the current security context holds an admin-level role
     * ({@code ROLE_ADMIN}, {@code ROLE_SUPER_ADMIN}, or {@code ROLE_SYSTEM_ADMIN}).
     */
    public static boolean isAdminUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority.getAuthority() != null && ADMIN_ROLES.contains(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeRole(String groupName) {
        if (groupName == null) {
            return null;
        }
        String raw = groupName.trim();
        if (raw.isEmpty()) {
            return null;
        }

        String normalized = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        return normalized;
    }

    public static String[] parseCsvAuthorities(String csv, String... defaults) {
        String[] parsed = (csv == null || csv.trim().isEmpty())
                ? new String[0]
                : Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new);

        if (parsed.length > 0) {
            return parsed;
        }

        if (defaults == null || defaults.length == 0) {
            return new String[0];
        }

        return Arrays.stream(defaults)
                .filter(s -> s != null && !s.trim().isEmpty())
                .map(String::trim)
                .toArray(String[]::new);
    }
}

