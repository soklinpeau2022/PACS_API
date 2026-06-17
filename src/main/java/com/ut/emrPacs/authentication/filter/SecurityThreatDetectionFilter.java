package com.ut.emrPacs.authentication.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ut.emrPacs.helper.security.SecurityAuditLogger;
import com.ut.emrPacs.helper.security.SecurityIncidentReporter;
import com.ut.emrPacs.helper.security.UnicodeGuard;
import com.ut.emrPacs.helper.http.RequestClientInfoHelper;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import jakarta.servlet.http.Cookie;

@Component
public class SecurityThreatDetectionFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityThreatDetectionFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final int MAX_VIEWER_STATE_BODY_BYTES = 12 * 1024 * 1024;
    private static final String GENERIC_ERROR_MESSAGE = "Invalid request parameters.";
    private static final String FORBIDDEN_MESSAGE = "Forbidden";
    private static final String PAYLOAD_TOO_LARGE_MESSAGE = "Request payload too large.";

    @Value("${cors.allowedOrigins:}")
    private String allowedOriginsCsv;

    private final SecurityIncidentReporter securityIncidentReporter;
    private volatile Set<String> trustedOrigins;

    public SecurityThreatDetectionFilter() {
        this(null);
    }

    @Autowired
    public SecurityThreatDetectionFilter(@Lazy SecurityIncidentReporter securityIncidentReporter) {
        this.securityIncidentReporter = securityIncidentReporter;
    }

    private static final Set<String> DICOM_SERVER_URL_FIELDS = Set.of(
            "ipAddress",
            "baseUrl",
            "dicomServerBaseUrl",
            "dicomServerUiBaseUrl",
            "dicomwebBaseUrl",
            "publicHealthCheckUrl",
            "public_health_check_url",
            "healthCheckUrl",
            "pingUrl",
            "publicPingUrl",
            "viewerBaseUrl",
            "pacsApiCallbackBaseUrl",
            "pacsApiAuthCallback",
            "pacs_api_callback_base_url"
    );
    private static final Set<String> UDAYA_DICOM_SERVER_AUTH_TOKEN_FIELDS = Set.of(
            "token-key",
            "token-value",
            "tokenKey",
            "tokenValue"
    );
    private static final Set<String> PACS_VIEWER_STATE_PAYLOAD_FIELDS = Set.of(
            "viewerState",
            "measurements",
            "annotations",
            "segmentations",
            "labelmapSegmentations",
            "contourSegmentations",
            "surfaceSegmentations",
            "additionalFindings",
            "presentationState",
            "toolState",
            "metadata",
            "chunkData"
    );

    private static final List<ThreatPattern> THREAT_PATTERNS = List.of(
            // XSS: script tags
            new ThreatPattern("xss-script", Pattern.compile("(?i)<\\s*script\\b")),
            // XSS: dangerous URI protocols (includes whitespace-split bypass e.g. java\tscript:)
            new ThreatPattern("xss-proto", Pattern.compile("(?i)(?:j\\s*a\\s*v\\s*a\\s*s\\s*c\\s*r\\s*i\\s*p\\s*t|v\\s*b\\s*s\\s*c\\s*r\\s*i\\s*p\\s*t|data)\\s*:")),
            // XSS: all DOM event handlers (comprehensive)
            new ThreatPattern("xss-event-handler", Pattern.compile(
                    "(?i)on(?:error|load|click|mouseover|mouseout|mousedown|mouseup|mousemove|dblclick" +
                    "|focus|blur|change|input|submit|reset|select|keydown|keyup|keypress" +
                    "|abort|unload|beforeunload|resize|scroll|contextmenu|wheel" +
                    "|drag|dragstart|dragend|dragenter|dragleave|dragover|drop" +
                    "|copy|cut|paste|touchstart|touchend|touchmove|pointerdown|pointerup" +
                    "|animationstart|animationend|transitionend|hashchange|popstate|message" +
                    "|online|offline|storage|visibilitychange)\\s*="
            )),
            // XSS: CSS expression injection and dangerous HTML tags
            new ThreatPattern("xss-css-expr", Pattern.compile("(?i)expression\\s*\\(")),
            new ThreatPattern("xss-html-inject", Pattern.compile("(?i)<\\s*(?:iframe|object|embed|applet|base|form|meta|link|svg|math|img\\s+[^>]*src)\\b")),
            // SQL Injection: UNION-based (including comment-obfuscated)
            new ThreatPattern("sql-union", Pattern.compile("(?i)\\bunion(?:\\s|/\\*.*?\\*/)+(?:all(?:\\s|/\\*.*?\\*/)+)?select\\b")),
            // SQL Injection: boolean-based blind (numeric AND string forms)
            new ThreatPattern("sql-boolean-num", Pattern.compile("(?i)(?:'|%27)?\\s*\\b(or|and)\\b\\s+\\d+\\s*=\\s*\\d+")),
            new ThreatPattern("sql-boolean-str", Pattern.compile("(?i)(?:'|%27)\\s*(?:or|and)\\s*(?:'|%27)[^']*(?:'|%27)\\s*=\\s*(?:'|%27)")),
            // SQL Injection: time-based blind
            new ThreatPattern("sql-time", Pattern.compile("(?i)\\b(?:sleep|benchmark|pg_sleep)\\s*\\(")),
            new ThreatPattern("sql-waitfor", Pattern.compile("(?i)\\bwaitfor\\s+delay\\b")),
            // SQL Injection: stacked queries / dangerous statements
            new ThreatPattern("sql-stacked", Pattern.compile("(?i);\\s*(?:drop|alter|truncate|create|insert|update|delete|exec|execute|call)\\b")),
            // SQL Injection: encoded/obfuscated tricks
            new ThreatPattern("sql-char-encode", Pattern.compile("(?i)\\b(?:char|chr|nchar|ascii|hex)\\s*\\(\\s*\\d")),
            new ThreatPattern("sql-cast", Pattern.compile("(?i)\\bcast\\s*\\([^)]*\\bas\\b")),
            // SQL Injection: comment-based (catches no-space obfuscation via /**/)
            new ThreatPattern("sql-comment", Pattern.compile("(?i)(?:/\\*|\\*/|--(?:\\s|$)|#(?:\\s|$))")),
            // Template/Expression Language Injection: EL, SSTI (includes Thymeleaf [( syntax)
            new ThreatPattern("template-el", Pattern.compile("(?i)(?:\\$\\{|#\\{|@\\{|\\{\\{|\\}\\}|<%|%>|\\[\\[|\\[\\(|\\[#)")),
            new ThreatPattern("template-ssti", Pattern.compile("(?i)\\b(?:freemarker|velocity|thymeleaf|jinja|twig|smarty|erb|handlebars)\\b")),
            // Path Traversal (standard + URL-encoded + overlong UTF-8 variants)
            new ThreatPattern("path-traversal", Pattern.compile("(?i)(?:\\.\\./|\\.\\.\\\\|%2e%2e(?:%2f|%5c|/|\\\\)|(?:%252e){2}(?:%252f|%255c)|%c0%ae|%c0%af|%e0%80%ae|%ef%bc%8f)")),
            // Null byte injection
            new ThreatPattern("null-byte", Pattern.compile("[\\x00]")),
            // HTTP Response Splitting: block CRLF used for header injection OR body injection (double CRLF)
            new ThreatPattern("crlf-inject", Pattern.compile("(?:%0[dD]|%0[aA]|\\r\\n|\\r|\\n)(?:[A-Za-z][A-Za-z0-9-]*\\s*:|(?:%0[dD]|%0[aA]|\\r\\n|\\r|\\n))")),
            // XSS: HTML entity-encoded characters in URL-like context (e.g. &#106;avascript:)
            new ThreatPattern("xss-entity-proto", Pattern.compile("(?i)&#[xX]?[0-9a-fA-F]{1,6};[a-zA-Z]*:")),
            // LDAP Injection
            new ThreatPattern("ldap-inject", Pattern.compile("(?i)(?:\\*\\)|\\(\\||!\\(|\\)\\(|\\*\\(uid|\\*\\(cn)")),
            // XML/XXE Injection
            new ThreatPattern("xxe-inject", Pattern.compile("(?i)(?:<!\\s*ENTITY|<!\\s*DOCTYPE|SYSTEM\\s+['\"]|PUBLIC\\s+['\"])")),
            // OS Command Injection (includes python3, ruby3, php8 versioned variants and /path/ prefixed)
            new ThreatPattern("cmd-inject", Pattern.compile("(?i)(?:[;&|`]\\s*(?:/[a-z/]*)?(?:cat|ls|pwd|id|whoami|uname|curl|wget|nc|netcat|bash|sh|zsh|ksh|cmd|powershell|python\\d*|perl\\d*|ruby\\d*|php\\d*)\\b|\\$\\(|`[^`]+`)")),
            // SSRF: internal IPs via http/https (standard dotted-decimal form)
            new ThreatPattern("ssrf-http", Pattern.compile("(?i)https?://(?:localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|169\\.254\\.|10\\.|172\\.(?:1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.|::1|\\[::1\\]|\\[::ffff:|metadata\\.google\\.internal|169\\.254\\.169\\.254)")),
            // SSRF: alternative IP notation bypasses (octal, decimal, hex, short-form)
            new ThreatPattern("ssrf-alt-ip", Pattern.compile("(?i)https?://(?:0x[0-9a-f]+\\.|0\\d+\\.|\\d{8,10}(?:[:/]|$)|127\\.\\d+(?:[:/]|$))")),
            // SSRF: dangerous non-HTTP protocols (file, gopher, dict, ftp, ldap, etc.)
            new ThreatPattern("ssrf-proto", Pattern.compile("(?i)(?:file|gopher|dict|ftp|ldap|ldaps|sftp|tftp|jar|netdoc|mailto)://"))
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Host Header Injection: block CR/LF in Host header (HTTP response splitting via Host)
        String hostHeader = request.getHeader("Host");
        if (hostHeader != null && (hostHeader.contains("\r") || hostHeader.contains("\n") || hostHeader.contains("%0d") || hostHeader.contains("%0a"))) {
            SecurityAuditLogger.logBlocked(LOGGER, request, "host_header_injection", "crlf_in_host", null);
            reportBlocked(request, "host_header_injection", "crlf_in_host", "Host header contains CR/LF");
            denyRequest(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", GENERIC_ERROR_MESSAGE);
            return;
        }

        if (shouldEnforceSameOrigin(request) && !isSameOrigin(request)) {
            SecurityAuditLogger.logBlocked(LOGGER, request, "csrf_same_origin", "origin_mismatch", null);
            reportBlocked(request, "csrf_same_origin", "origin_mismatch", request.getHeader("Origin"));
            denyRequest(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", FORBIDDEN_MESSAGE);
            return;
        }

        ThreatMatch pathOrQueryMatch = findThreatInPathOrQuery(request);
        if (pathOrQueryMatch != null) {
            denyRequest(request, response, pathOrQueryMatch);
            return;
        }

        if (!shouldInspectBody(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        int maxBodyBytes = resolveMaxBodyBytes(request);
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBodyBytes) {
            SecurityAuditLogger.logBlocked(LOGGER, request, "payload_too_large", "content_length", Long.toString(contentLength));
            reportBlocked(request, "payload_too_large", "content_length", contentLength + "/" + maxBodyBytes);
            denyRequest(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "PAYLOAD_TOO_LARGE", PAYLOAD_TOO_LARGE_MESSAGE);
            return;
        }

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, maxBodyBytes);
        if (wrapped.isBodyTooLarge()) {
            SecurityAuditLogger.logBlocked(LOGGER, request, "payload_too_large", "stream_limit", null);
            reportBlocked(request, "payload_too_large", "stream_limit", Integer.toString(maxBodyBytes));
            denyRequest(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "PAYLOAD_TOO_LARGE", PAYLOAD_TOO_LARGE_MESSAGE);
            return;
        }
        String body = wrapped.getCachedBodyAsString();
        if (UnicodeGuard.containsDisallowed(body)) {
            denyRequest(request, response, new ThreatMatch("body", "unicode_control"));
            return;
        }
        ThreatMatch bodyMatch = findThreat(redactAllowedSecurityFields(request, body), "body");
        if (bodyMatch != null) {
            denyRequest(request, response, bodyMatch);
            return;
        }

        filterChain.doFilter(wrapped, response);
    }

    private ThreatMatch findThreatInPathOrQuery(HttpServletRequest request) {
        String rawPath = request.getRequestURI();
        ThreatMatch match = findThreatWithDecoding(rawPath, "path");
        if (match != null) {
            return match;
        }

        String rawQuery = request.getQueryString();
        match = findThreatWithDecoding(rawQuery, "query");
        if (match != null) {
            return match;
        }

        return null;
    }

    private ThreatMatch findThreatWithDecoding(String value, String area) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (UnicodeGuard.containsDisallowed(value)) {
            return new ThreatMatch(area, "unicode_control");
        }
        ThreatMatch match = findThreat(value, area);
        if (match != null) {
            return match;
        }

        String decoded = safeUrlDecode(value);
        if (decoded != null && !decoded.equals(value)) {
            if (UnicodeGuard.containsDisallowed(decoded)) {
                return new ThreatMatch(area + "(decoded)", "unicode_control");
            }
            match = findThreat(decoded, area + "(decoded)");
            if (match != null) {
                return match;
            }

            String decodedTwice = safeUrlDecode(decoded);
            if (decodedTwice != null && !decodedTwice.equals(decoded)) {
                if (UnicodeGuard.containsDisallowed(decodedTwice)) {
                    return new ThreatMatch(area + "(decoded2)", "unicode_control");
                }
                return findThreat(decodedTwice, area + "(decoded2)");
            }
        }

        return null;
    }

    private static String redactAllowedSecurityFields(HttpServletRequest request, String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            if (isDicomServerConfigPath(request)) {
                redactAllowedFields(root, DICOM_SERVER_URL_FIELDS, "[configured-url]");
            }
            if (isDicomServerViewerAuthorizationPath(request)) {
                redactAllowedFields(root, UDAYA_DICOM_SERVER_AUTH_TOKEN_FIELDS, "[viewer-token]");
            }
            if (isPacsViewerStatePath(request)) {
                redactAllowedFields(root, PACS_VIEWER_STATE_PAYLOAD_FIELDS, "[viewer-state]");
            }
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception ignored) {
            return body;
        }
    }

    private static boolean isDicomServerConfigPath(HttpServletRequest request) {
        if (request == null || request.getRequestURI() == null) {
            return false;
        }
        String path = request.getRequestURI();
        return path.endsWith("/dicom-server/dicom-server-create")
                || path.endsWith("/dicom-server/dicom-server-update");
    }

    private static boolean isDicomServerViewerAuthorizationPath(HttpServletRequest request) {
        if (request == null || request.getRequestURI() == null) {
            return false;
        }
        String path = request.getRequestURI();
        return path.endsWith("/worklist/viewer-dicom-web-authorize")
                || path.endsWith("/worklist/viewer-dicom-web-proxy-authorize")
                || path.endsWith("/worklist/viewer-dicom-web-decode")
                || path.endsWith("/worklist/viewer-dicom-web-profile");
    }

    private static boolean isPacsViewerStatePath(HttpServletRequest request) {
        if (request == null || request.getRequestURI() == null) {
            return false;
        }
        String path = request.getRequestURI();
        return path.contains("/pacs-result-api/pacs-result-viewer-state-")
                || path.contains("/pacs-result/pacs-result-viewer-state-");
    }

    private static int resolveMaxBodyBytes(HttpServletRequest request) {
        return isPacsViewerStatePath(request) ? MAX_VIEWER_STATE_BODY_BYTES : MAX_BODY_BYTES;
    }

    private static void redactAllowedFields(JsonNode node, Set<String> fieldNames, String replacement) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            for (String fieldName : fieldNames) {
                if (objectNode.has(fieldName)) {
                    objectNode.put(fieldName, replacement);
                }
            }
            objectNode.fields().forEachRemaining(entry -> redactAllowedFields(entry.getValue(), fieldNames, replacement));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> redactAllowedFields(child, fieldNames, replacement));
        }
    }

    private ThreatMatch findThreat(String value, String area) {
        if (value == null || value.isBlank()) {
            return null;
        }
        boolean bodyArea = area != null && area.startsWith("body");
        for (ThreatPattern pattern : THREAT_PATTERNS) {
            // CRLF header-injection detection should not block normal multi-line JSON bodies.
            if (bodyArea && "crlf-inject".equals(pattern.name())) {
                continue;
            }
            if (pattern.pattern().matcher(value).find()) {
                return new ThreatMatch(area, pattern.name());
            }
        }
        return null;
    }

    private void denyRequest(HttpServletRequest request, HttpServletResponse response, ThreatMatch match) throws IOException {
        String ip = RequestClientInfoHelper.resolveClientIp(request);
        LOGGER.warn("Blocked request: threat={} area={} path={} ip={}", match.patternName(), match.area(), request.getRequestURI(), ip);
        SecurityAuditLogger.logBlocked(LOGGER, request, "threat_detected", match.patternName(), match.area());
        reportBlocked(request, "threat_detected", match.patternName(), match.area());

        denyRequest(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", GENERIC_ERROR_MESSAGE);
    }

    private void reportBlocked(HttpServletRequest request, String event, String reason, String detail) {
        if (securityIncidentReporter != null) {
            securityIncidentReporter.reportBlockedRequest(request, event, reason, detail);
        }
    }

    private void denyRequest(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String json = OBJECT_MAPPER.writeValueAsString(
                ResponseMessageUtils.makeResponse(false, status, code, message)
        );
        response.getWriter().write(json);
    }

    private boolean shouldInspectBody(HttpServletRequest request) {
        String method = request.getMethod();
        if (method == null) {
            return false;
        }
        String normalized = method.toUpperCase(Locale.ROOT);
        if (!normalized.equals("POST") && !normalized.equals("PUT") && !normalized.equals("PATCH") && !normalized.equals("DELETE")) {
            return false;
        }

        String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return false;
        }

        String lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("multipart/")
                || lower.startsWith("application/octet-stream")
                || lower.startsWith("image/")
                || lower.startsWith("video/")
                || lower.startsWith("audio/")
                || lower.startsWith("application/pdf")) {
            return false;
        }

        long contentLength = request.getContentLengthLong();
        if (contentLength < 0) {
            return true;
        }
        return contentLength > 0;
    }

    private static String safeUrlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record ThreatPattern(String name, Pattern pattern) {}

    private record ThreatMatch(String area, String patternName) {}

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;
        private final boolean bodyTooLarge;

        private CachedBodyHttpServletRequest(HttpServletRequest request, int maxBytes) throws IOException {
            super(request);
            BodyReadResult result = readWithLimit(request, maxBytes);
            this.cachedBody = result.body();
            this.bodyTooLarge = result.tooLarge();
        }

        public String getCachedBodyAsString() {
            if (cachedBody.length == 0) {
                return "";
            }
            Charset charset = resolveCharset();
            return new String(cachedBody, charset);
        }

        public boolean isBodyTooLarge() {
            return bodyTooLarge;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream buffer = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return buffer.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Not used
                }

                @Override
                public int read() {
                    return buffer.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), resolveCharset()));
        }

        private Charset resolveCharset() {
            String encoding = getCharacterEncoding();
            if (encoding == null || encoding.isBlank()) {
                return StandardCharsets.UTF_8;
            }
            try {
                return Charset.forName(encoding);
            } catch (Exception ex) {
                return StandardCharsets.UTF_8;
            }
        }
    }

    private record BodyReadResult(byte[] body, boolean tooLarge) {}

    private static BodyReadResult readWithLimit(HttpServletRequest request, int maxBytes) throws IOException {
        if (maxBytes <= 0) {
            return new BodyReadResult(new byte[0], false);
        }
        byte[] buffer = new byte[8192];
        int total = 0;
        boolean tooLarge = false;
        long contentLength = request.getContentLengthLong();
        long remaining = contentLength >= 0 ? contentLength : Long.MAX_VALUE;
        try (ServletInputStream in = request.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int read;
            while (remaining > 0 && (read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                total += read;
                remaining -= read;
                if (total > maxBytes) {
                    tooLarge = true;
                    break;
                }
                out.write(buffer, 0, read);
            }
            return new BodyReadResult(out.toByteArray(), tooLarge);
        }
    }

    private static boolean shouldEnforceSameOrigin(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String method = request.getMethod();
        if (method == null) {
            return false;
        }
        String m = method.toUpperCase(Locale.ROOT);
        if (m.equals("GET") || m.equals("HEAD") || m.equals("OPTIONS")) {
            return false;
        }
        return hasAuthCookies(request);
    }

    private static boolean hasAuthCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (cookie == null || cookie.getName() == null) {
                continue;
            }
            String name = cookie.getName();
            if ("refreshToken".equals(name) || "accessToken".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameOrigin(HttpServletRequest request) {
        String originHeader = request.getHeader("Origin");
        String refererHeader = request.getHeader("Referer");
        String source = (originHeader != null && !originHeader.isBlank()) ? originHeader : refererHeader;
        if (source == null || source.isBlank()) {
            return true;
        }

        URI sourceUri;
        try {
            sourceUri = new URI(source);
        } catch (Exception ex) {
            return false;
        }

        String sourceScheme = sourceUri.getScheme();
        String sourceHost = sourceUri.getHost();
        int sourcePort = sourceUri.getPort();
        if (sourceScheme == null || sourceHost == null) {
            return false;
        }
        if (sourcePort == -1) {
            sourcePort = "https".equalsIgnoreCase(sourceScheme) ? 443 : 80;
        }

        String reqScheme = resolveRequestScheme(request);
        String reqHost = resolveRequestHost(request);
        int reqPort = resolveRequestPort(request, reqScheme);

        if (reqScheme == null || reqHost == null) {
            return false;
        }

        boolean requestOriginMatches = sourceScheme.equalsIgnoreCase(reqScheme)
                && sourceHost.equalsIgnoreCase(reqHost)
                && sourcePort == reqPort;
        return requestOriginMatches || getTrustedOrigins().contains(normalizeOrigin(sourceUri));
    }

    private Set<String> getTrustedOrigins() {
        Set<String> cached = trustedOrigins;
        if (cached != null) {
            return cached;
        }

        Set<String> parsed = new java.util.LinkedHashSet<>();
        if (allowedOriginsCsv != null && !allowedOriginsCsv.isBlank()) {
            for (String value : allowedOriginsCsv.split(",")) {
                String candidate = value == null ? "" : value.trim();
                if (candidate.isEmpty() || "*".equals(candidate)) {
                    continue;
                }
                try {
                    String origin = normalizeOrigin(new URI(candidate));
                    if (origin != null) {
                        parsed.add(origin);
                    }
                } catch (Exception ignored) {
                    // Invalid configured origins are ignored and therefore remain blocked.
                }
            }
        }

        trustedOrigins = Set.copyOf(parsed);
        return trustedOrigins;
    }

    private static String normalizeOrigin(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return null;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equals(scheme) ? 443 : 80;
        }
        return scheme + "://" + host + ":" + port;
    }

    private static String resolveRequestScheme(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-Proto");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getScheme();
    }

    private static String resolveRequestHost(HttpServletRequest request) {
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null && !forwardedHost.isBlank()) {
            String hostPart = forwardedHost.split(",")[0].trim();
            return hostPart.split(":")[0].trim();
        }
        String hostHeader = request.getHeader("Host");
        if (hostHeader != null && !hostHeader.isBlank()) {
            return hostHeader.split(":")[0].trim();
        }
        return request.getServerName();
    }

    private static int resolveRequestPort(HttpServletRequest request, String scheme) {
        String forwardedPort = request.getHeader("X-Forwarded-Port");
        if (forwardedPort != null && !forwardedPort.isBlank()) {
            try {
                return Integer.parseInt(forwardedPort.split(",")[0].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        String hostHeader = request.getHeader("Host");
        if (hostHeader != null && hostHeader.contains(":")) {
            String[] parts = hostHeader.split(":");
            if (parts.length == 2) {
                try {
                    return Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (scheme == null) {
            return request.getServerPort();
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return request.getServerPort() > 0 ? request.getServerPort() : 443;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return request.getServerPort() > 0 ? request.getServerPort() : 80;
        }
        return request.getServerPort();
    }
}
