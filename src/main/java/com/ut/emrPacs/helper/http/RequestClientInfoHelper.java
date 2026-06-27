package com.ut.emrPacs.helper.http;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;

/**
 * HTTP request client information helper.
 *
 * <p>Resolves a best-effort client IP (proxy-aware) and produces a small OS/browser string from User-Agent.</p>
 */
public final class RequestClientInfoHelper {

    private RequestClientInfoHelper() {}

    /**
     * Resolves a client IP address from common reverse-proxy headers.
     *
     * <p>Header priority:</p>
     * <ol>
     *   <li>{@code X-Forwarded-For} (first value)</li>
     *   <li>{@code X-Real-IP}</li>
     *   <li>{@link HttpServletRequest#getRemoteAddr()}</li>
     * </ol>
     *
     * @return normalized client IP, or {@code ""} when request is null or no value is available.
     */
    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }

        // Always start with the direct peer address.
        String remoteAddr = normalizeIp(request.getRemoteAddr());

        // Security: only trust X-Forwarded-* headers when the direct peer looks like a trusted proxy.
        // This prevents clients from spoofing their IP by sending X-Forwarded-For directly.
        if (isPrivateOrLoopbackIp(remoteAddr)) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            String ipFromXff = firstForwardedForIp(xForwardedFor);
            if (!ipFromXff.isEmpty()) {
                return ipFromXff;
            }

            String realIp = normalizeIp(request.getHeader("X-Real-IP"));
            if (isValidIp(realIp)) {
                return realIp;
            }
        }

        return remoteAddr;
    }

    /**
     * Normalizes loopback and IPv4-mapped IPv6 addresses.
     */
    private static String normalizeIp(String ip) {
        if (ip == null) {
            return "";
        }
        String x = ip.trim();
        if (x.isEmpty()) {
            return "";
        }

        // Strip simple IPv4 ":port" form if present (XFF sometimes includes it).
        // Avoid touching IPv6 which contains ':' characters by design.
        int lastColon = x.lastIndexOf(':');
        if (lastColon > 0 && x.indexOf('.') > 0 && lastColon > x.indexOf('.')) {
            String maybePort = x.substring(lastColon + 1);
            if (maybePort.chars().allMatch(Character::isDigit)) {
                x = x.substring(0, lastColon);
            }
        }

        if ("0:0:0:0:0:0:0:1".equals(x) || "::1".equals(x)) {
            return "127.0.0.1";
        }
        if (x.startsWith("::ffff:")) {
            return x.substring("::ffff:".length());
        }
        return x;
    }

    private static String firstForwardedForIp(String xForwardedFor) {
        if (xForwardedFor == null || xForwardedFor.trim().isEmpty()) {
            return "";
        }
        String[] parts = xForwardedFor.split(",");
        if (parts.length == 0) {
            return "";
        }
        String first = normalizeIp(parts[0].trim());
        return isValidIp(first) ? first : "";
    }

    private static boolean isValidIp(String ip) {
        if (ip == null) {
            return false;
        }
        String x = ip.trim();
        if (x.isEmpty()) {
            return false;
        }
        if (x.length() > 64) {
            return false;
        }

        // Allow only common IP characters (no hostnames, no spaces).
        for (int i = 0; i < x.length(); i++) {
            char c = x.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F')
                    || c == '.'
                    || c == ':';
            if (!ok) {
                return false;
            }
        }

        if (x.indexOf('.') >= 0) {
            return isValidIpv4(x);
        }
        return x.indexOf(':') >= 0; // best-effort IPv6 check
    }

    private static boolean isValidIpv4(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String p : parts) {
            if (p.isEmpty() || p.length() > 3) {
                return false;
            }
            if (!p.chars().allMatch(Character::isDigit)) {
                return false;
            }
            int n = Integer.parseInt(p);
            if (n < 0 || n > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPrivateOrLoopbackIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String x = ip.trim().toLowerCase(Locale.ROOT);

        // IPv6 loopback/ULA/link-local (common for internal proxies)
        if (x.equals("127.0.0.1") || x.equals("::1")) return true;
        if (x.startsWith("fc") || x.startsWith("fd")) return true;  // fc00::/7
        if (x.startsWith("fe80:")) return true;                    // fe80::/10

        // IPv4 private ranges + loopback
        if (!isValidIpv4(x)) {
            return false;
        }
        String[] parts = x.split("\\.");
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[1]);

        if (a == 10) return true;
        if (a == 127) return true;
        if (a == 192 && b == 168) return true;
        return a == 172 && b >= 16 && b <= 31;
    }

    /**
     * Returns {@code true} when {@code host} is a loopback address.
     *
     * <p>Recognized values: {@code localhost}, {@code ::1}, any {@code 127.x.x.x}.</p>
     */
    public static boolean isLoopbackHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "::1".equals(normalized)
                || normalized.startsWith("127.");
    }

    /**
     * Strips a {@code Bearer } prefix from a token header value, if present.
     *
     * @param tokenValue raw Authorization header value or bare token string
     * @return the token value without the prefix, or {@code ""} if blank
     */
    public static String extractBearerToken(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return "";
        }
        String token = tokenValue.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return token.substring("Bearer ".length()).trim();
        }
        return token;
    }

    /**
     * Formats a small "OS: ... Browser: ..." string based on {@code User-Agent} header.
     */
    public static String formatUserAgent(HttpServletRequest request) {
        if (request == null) {
            return formatUserAgent((String) null);
        }
        return formatUserAgent(request.getHeader("User-Agent"));
    }

    /**
     * Formats a small "OS: ... Browser: ..." string based on a raw User-Agent string.
     */
    public static String formatUserAgent(String userAgent) {
        if (userAgent == null) {
            return "OS: Unknown Browser: Unknown";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);

        String os;
        if (ua.contains("windows")) {
            os = "Windows";
        } else if (ua.contains("android")) {
            os = "Android";
        } else if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            os = "iOS";
        } else if (ua.contains("mac os x") || ua.contains("macintosh")) {
            os = "Mac";
        } else if (ua.contains("linux")) {
            os = "Linux";
        } else {
            os = "Unknown";
        }

        String browser;
        if (ua.contains("edg/") || ua.contains("edge/")) {
            browser = "Edge";
        } else if (ua.contains("chrome/") && !ua.contains("edg/")) {
            browser = "Chrome";
        } else if (ua.contains("firefox/")) {
            browser = "Firefox";
        } else if (ua.contains("safari/") && !ua.contains("chrome/")) {
            browser = "Safari";
        } else if (ua.contains("msie") || ua.contains("trident/")) {
            browser = "Internet Explorer";
        } else {
            browser = "Unknown";
        }

        return "OS: " + os + " Browser: " + browser;
    }
}
