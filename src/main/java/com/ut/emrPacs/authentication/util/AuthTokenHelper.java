package com.ut.emrPacs.authentication.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;

/**
 * Shared helpers for resolving tokens from requests without duplicating logic across filters/services.
 */
public final class AuthTokenHelper {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private AuthTokenHelper() {
    }

    public static String resolveBearerToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return resolveBearerToken(request.getHeader(AUTHORIZATION_HEADER));
    }

    public static String resolveBearerToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }

        String header = authorizationHeader.trim();
        if (header.length() <= BEARER_PREFIX.length()) {
            return null;
        }

        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty() || isNullLike(token)) {
            return null;
        }

        return token;
    }

    public static boolean isNullLike(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || normalized.equals("null") || normalized.equals("undefined");
    }

}
