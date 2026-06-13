package com.ut.emrPacs.authentication.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;

public class CookieUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(CookieUtils.class);
    private static final String DEFAULT_PATH = "/";

    /**
     * Retrieves a cookie by name from the request.
     *
     * @param request The HTTP request.
     * @param name    The name of the cookie.
     * @return An optional containing the cookie if found.
     */
    public static Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return Optional.of(cookie);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Adds or updates a cookie in the response.
     *
     * @param request    The HTTP request.
     * @param response   The HTTP response.
     * @param name       The name of the cookie.
     * @param value      The value of the cookie.
     * @param maxAge     The max age (in seconds) before the cookie expires.
     * @param isHttpOnly Whether the cookie should be HttpOnly.
     * @param sameSite   The SameSite attribute (Strict, Lax, None).
     */
    public static void addOrUpdateCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            String name,
            String value,
            int maxAge,
            boolean isHttpOnly,
            String sameSite
    ) {
        addOrUpdateCookie(request, response, name, value, maxAge, isHttpOnly, sameSite, true);
    }

    public static void addOrUpdateCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            String name,
            String value,
            int maxAge,
            boolean isHttpOnly,
            String sameSite,
            boolean isSecure
    ) {
        String normalizedSameSite = normalizeSameSite(sameSite);
        if ("None".equalsIgnoreCase(normalizedSameSite) && !isSecure) {
            // SameSite=None without Secure is rejected by modern browsers; prefer Lax over a broken cookie.
            LOGGER.warn("Cookie misconfiguration: SameSite=None requires Secure. Using SameSite=Lax for cookie: {}", name);
            normalizedSameSite = "Lax";
        }

        String path = resolveCookiePath(request);

        // Prefer a single explicit Set-Cookie header so SameSite is applied consistently.
        // (The Servlet Cookie API doesn't reliably support SameSite across containers.)
        StringBuilder cookieHeader = new StringBuilder();
        cookieHeader.append(name).append("=").append(value == null ? "" : value)
                .append("; Path=").append(path)
                .append("; Max-Age=").append(maxAge);

        if (isHttpOnly) {
            cookieHeader.append("; HttpOnly");
        }

        if (isSecure) {
            cookieHeader.append("; Secure");
        }

        if (normalizedSameSite != null) {
            cookieHeader.append("; SameSite=").append(normalizedSameSite);
        }

        response.addHeader("Set-Cookie", cookieHeader.toString());
    }

    /**
     * Deletes a cookie from the response by setting its max age to 0.
     *
     * @param request   The HTTP request.
     * @param response  The HTTP response.
     * @param name      The name of the cookie to delete.
     * @param isHttpOnly Whether the cookie should be HttpOnly.
     * @param sameSite  The SameSite attribute (Strict, Lax, None).
     * @param isSecure  Whether the cookie should be Secure.
     */
    public static void deleteCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            String name,
            boolean isHttpOnly,
            String sameSite,
            boolean isSecure
    ) {
        String normalizedSameSite = normalizeSameSite(sameSite);
        String path = resolveCookiePath(request);

        // Always send a deletion Set-Cookie header; do not depend on request cookies being present.
        StringBuilder cookieHeader = new StringBuilder();
        cookieHeader.append(name)
                .append("=; Path=").append(path)
                .append("; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT");

        if (isHttpOnly) {
            cookieHeader.append("; HttpOnly");
        }

        if (isSecure) {
            cookieHeader.append("; Secure");
        }

        if (normalizedSameSite != null) {
            cookieHeader.append("; SameSite=").append(normalizedSameSite);
        }

        response.addHeader("Set-Cookie", cookieHeader.toString());
        LOGGER.debug("Cookie deleted: {}", name);
    }

    private static String resolveCookiePath(HttpServletRequest request) {
        if (request == null) {
            return DEFAULT_PATH;
        }

        String contextPath = request.getContextPath();
        if (contextPath == null) {
            return DEFAULT_PATH;
        }

        String trimmed = contextPath.trim();
        if (trimmed.isEmpty() || "/".equals(trimmed)) {
            return DEFAULT_PATH;
        }

        return trimmed.startsWith("/") ? trimmed : DEFAULT_PATH;
    }

    private static String normalizeSameSite(String sameSite) {
        if (sameSite == null) {
            return null;
        }
        String v = sameSite.trim();
        if (v.isEmpty()) {
            return null;
        }

        return switch (v.toLowerCase(Locale.ROOT)) {
            case "lax" -> "Lax";
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> null;
        };
    }
}
