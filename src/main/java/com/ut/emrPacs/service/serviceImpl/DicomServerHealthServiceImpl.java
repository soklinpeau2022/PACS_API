package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.model.dto.response.pacs.dicom.DicomServerHealthResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.DicomServerHealthSettingsResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.DicomServerHealthSummaryResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.service.service.DicomServerHealthService;
import static com.ut.emrPacs.helper.FunctionHelper.trimToNull;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DicomServerHealthServiceImpl implements DicomServerHealthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomServerHealthServiceImpl.class);
    private static final String STATUS_ONLINE = "ONLINE";
    private static final String STATUS_OFFLINE = "OFFLINE";
    private static final String STATUS_UNKNOWN = "UNKNOWN";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String SUMMARY_DISABLED = "DISABLED";
    private static final String SUMMARY_NO_SERVERS = "NO_SERVERS";
    private static final String SUMMARY_DEGRADED = "DEGRADED";
    private static final String SUMMARY_LIVE = "LIVE";
    private static final String SUMMARY_CHECKING = "CHECKING";
    private static final String SETTING_HEALTH_ENABLED = "dicom.server.health.enabled";
    private static final String SETTING_HEALTH_INTERVAL = "dicom.server.health.poll_interval_seconds";
    private static final String DICOM_SERVER_PREFIX = "dicom_server";
    private static final String LEGACY_DICOM_SERVER_PREFIX = "udaya_dicom_server";
    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 5;
    private static final int MIN_POLL_INTERVAL_SECONDS = 5;
    private static final int MAX_POLL_INTERVAL_SECONDS = 300;
    private static final int DEFAULT_OFFLINE_FAILURE_THRESHOLD = 3;
    private static final int MAX_OFFLINE_FAILURE_THRESHOLD = 20;
    private static final long DEFAULT_OFFLINE_GRACE_MS = 180_000L;
    private static final String DEFAULT_LOOPBACK_HOST_OVERRIDE = "";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    private final DicomServerMapper dicomServerMapper;
    private final JdbcTemplate jdbcTemplate;
    private final HttpClient httpClient;
    private final Map<Long, HealthSnapshot> snapshots = new ConcurrentHashMap<>();
    private volatile HealthProbeSettings cachedSettings;
    private volatile Instant settingsCacheUntil = Instant.EPOCH;
    private volatile Instant nextProbeAt = Instant.EPOCH;

    @Value("${dicom.server.health.timeout-ms:5000}")
    private long timeoutMs;

    @Value("${dicom.server.health.offline-failure-threshold:3}")
    private int offlineFailureThreshold;

    @Value("${dicom.server.health.offline-grace-ms:180000}")
    private long offlineGraceMs;

    @Value("${pacs.dicom-server.client.rewrite-loopback-in-container:true}")
    private boolean loopbackRewriteEnabled;

    @Value("${pacs.dicom-server.client.loopback-host-override:}")
    private String loopbackHostOverride;

    public DicomServerHealthServiceImpl(DicomServerMapper dicomServerMapper, JdbcTemplate jdbcTemplate) {
        this.dicomServerMapper = dicomServerMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Scheduled(
            initialDelayString = "${dicom.server.health.initial-delay-ms:1000}",
            fixedDelayString = "${dicom.server.health.scheduler-tick-ms:1000}"
    )
    public void probeActiveDicomServers() {
        HealthProbeSettings settings = loadSettings();
        if (!settings.enabled()) {
            return;
        }
        Instant now = Instant.now();
        if (now.isBefore(nextProbeAt)) {
            return;
        }
        nextProbeAt = now.plusSeconds(settings.pollIntervalSeconds());
        List<HospitalDicomServerResponse> servers = safeActiveServers(null);
        if (servers.isEmpty()) {
            return;
        }
        servers.parallelStream().forEach(this::probeAndStore);
    }

    @Override
    public void enrichDicomServerRows(List<HospitalDicomServerResponse> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        refreshMissingOrStaleSnapshots(rows, loadSettings());
        for (HospitalDicomServerResponse row : rows) {
            applySnapshot(row);
        }
    }

    @Override
    public List<DicomServerHealthResponse> listHealth(Long hospitalId) {
        List<HospitalDicomServerResponse> servers = safeActiveServers(hospitalId);
        refreshMissingOrStaleSnapshots(servers, loadSettings());
        return servers.stream()
                .map(this::toHealthResponse)
                .toList();
    }

    @Override
    public DicomServerHealthSummaryResponse getSummary(Long hospitalId) {
        HealthProbeSettings settings = loadSettings();
        List<DicomServerHealthResponse> rows = listHealth(hospitalId);
        int total = rows.size();
        int online = (int) rows.stream().filter(row -> Boolean.TRUE.equals(row.getOnline())).count();
        int offline = (int) rows.stream().filter(row -> STATUS_OFFLINE.equalsIgnoreCase(row.getStatus())).count();
        int unknown = Math.max(0, total - online - offline);

        DicomServerHealthSummaryResponse response = new DicomServerHealthSummaryResponse();
        response.setEnabled(settings.enabled());
        response.setPollIntervalSeconds(settings.pollIntervalSeconds());
        response.setTotalServers(total);
        response.setOnlineServers(online);
        response.setOfflineServers(offline);
        response.setUnknownServers(unknown);
        response.setCheckedAt(rows.stream()
                .map(DicomServerHealthResponse::getCheckedAt)
                .filter(value -> value != null && !value.isBlank())
                .max(String::compareTo)
                .orElse(null));
        if (!settings.enabled()) {
            response.setStatus(SUMMARY_DISABLED);
        } else if (total == 0) {
            response.setStatus(SUMMARY_NO_SERVERS);
        } else if (offline > 0) {
            response.setStatus(SUMMARY_DEGRADED);
        } else if (unknown > 0) {
            response.setStatus(SUMMARY_CHECKING);
        } else {
            response.setStatus(SUMMARY_LIVE);
        }
        return response;
    }

    @Override
    public DicomServerHealthSettingsResponse getSettings() {
        HealthProbeSettings settings = loadSettings();
        DicomServerHealthSettingsResponse response = new DicomServerHealthSettingsResponse();
        response.setEnabled(settings.enabled());
        response.setPollIntervalSeconds(settings.pollIntervalSeconds());
        return response;
    }

    @Override
    public DicomServerHealthSettingsResponse updateSettings(Boolean enabled, Integer pollIntervalSeconds, Long modifiedBy) {
        boolean normalizedEnabled = enabled == null || enabled;
        int normalizedInterval = normalizePollInterval(pollIntervalSeconds);
        upsertSetting(SETTING_HEALTH_ENABLED, Boolean.toString(normalizedEnabled), modifiedBy);
        upsertSetting(SETTING_HEALTH_INTERVAL, Integer.toString(normalizedInterval), modifiedBy);
        cachedSettings = new HealthProbeSettings(normalizedEnabled, normalizedInterval);
        settingsCacheUntil = Instant.now().plusSeconds(5);
        if (normalizedEnabled) {
            nextProbeAt = Instant.EPOCH;
        }
        return getSettings();
    }

    private List<HospitalDicomServerResponse> safeActiveServers(Long hospitalId) {
        try {
            List<HospitalDicomServerResponse> rows = dicomServerMapper.listActiveDicomServersForHealth(hospitalId);
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception error) {
            LOGGER.debug("Unable to read active DICOM servers for health probe: {}", error.getMessage());
            return Collections.emptyList();
        }
    }

    private void probeAndStore(HospitalDicomServerResponse server) {
        if (server == null || server.getId() == null) {
            return;
        }
        HealthSnapshot previous = snapshots.get(server.getId());
        HealthSnapshot next = probe(server, previous);
        snapshots.put(server.getId(), next);
    }

    private void refreshMissingOrStaleSnapshots(
            List<HospitalDicomServerResponse> servers,
            HealthProbeSettings settings
    ) {
        if (servers == null || servers.isEmpty() || settings == null || !settings.enabled()) {
            return;
        }
        Instant now = Instant.now();
        Duration maxAge = Duration.ofSeconds(settings.pollIntervalSeconds());
        servers.parallelStream()
                .filter(server -> server != null && server.getId() != null)
                .filter(server -> shouldRefreshSnapshot(server.getId(), now, maxAge))
                .forEach(this::probeAndStore);
    }

    private boolean shouldRefreshSnapshot(Long serverId, Instant now, Duration maxAge) {
        HealthSnapshot snapshot = snapshots.get(serverId);
        return snapshot == null
                || snapshot.checkedAt() == null
                || !snapshot.checkedAt().plus(maxAge).isAfter(now);
    }

    private HealthSnapshot probe(HospitalDicomServerResponse server, HealthSnapshot previous) {
        Instant checkedAt = Instant.now();
        long started = System.nanoTime();
        try {
            List<String> healthUrls = buildHealthUrls(server);
            Exception lastError = null;
            for (String candidateUrl : healthUrls) {
                try {
                    String healthUrl = rewriteHealthLoopbackUrlIfNeeded(candidateUrl);
                    HttpRequest.Builder builder = HttpRequest.newBuilder()
                            .uri(URI.create(healthUrl))
                            .timeout(Duration.ofMillis(Math.max(300L, timeoutMs)))
                            .GET()
                            .header("Accept", "application/json");
                    String username = trimToNull(server.getUsername());
                    String password = trimToNull(server.getPassword());
                    if (username != null && password != null) {
                        String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                        builder.header("Authorization", "Basic " + token);
                    }
                    HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
                    int statusCode = response.statusCode();
                    if (isReachableStatus(statusCode)) {
                        return buildSnapshot(true, checkedAt, previous, elapsedMs(started));
                    }
                    LOGGER.debug("DICOM server health probe returned {} for {} via {}", statusCode, server.getName(), healthUrl);
                } catch (Exception error) {
                    lastError = error;
                    LOGGER.debug("DICOM server health probe failed for {} via {}: {}", server.getName(), candidateUrl, error.getMessage());
                }
            }
            if (lastError != null) {
                LOGGER.debug("All DICOM server health probe URLs failed for {}: {}", server.getName(), lastError.getMessage());
            }
            return buildSnapshot(false, checkedAt, previous, elapsedMs(started));
        } catch (Exception error) {
            long responseTimeMs = elapsedMs(started);
            LOGGER.debug("DICOM server health probe failed for {}: {}", server.getName(), error.getMessage());
            return buildSnapshot(false, checkedAt, previous, responseTimeMs);
        }
    }

    private HealthSnapshot buildSnapshot(
            boolean online,
            Instant checkedAt,
            HealthSnapshot previous,
            Long responseTimeMs
    ) {
        if (online) {
            return new HealthSnapshot(
                    STATUS_ONLINE,
                    true,
                    checkedAt,
                    checkedAt,
                    null,
                    responseTimeMs,
                    0
            );
        }

        int consecutiveFailures = previous == null ? 1 : previous.consecutiveFailures() + 1;
        Instant lastOnlineAt = previous == null ? null : previous.lastOnlineAt();
        boolean hadKnownOnlineState = lastOnlineAt != null;
        boolean belowFailureThreshold = consecutiveFailures < normalizeOfflineFailureThreshold(offlineFailureThreshold);
        boolean insideGraceWindow = hadKnownOnlineState
                && !checkedAt.isAfter(lastOnlineAt.plusMillis(normalizeOfflineGraceMs(offlineGraceMs)));
        if (belowFailureThreshold || insideGraceWindow) {
            return new HealthSnapshot(
                    hadKnownOnlineState ? STATUS_ONLINE : STATUS_UNKNOWN,
                    hadKnownOnlineState,
                    checkedAt,
                    lastOnlineAt,
                    null,
                    responseTimeMs,
                    consecutiveFailures
            );
        }

        Instant offlineSince = previous != null && STATUS_OFFLINE.equals(previous.status()) && previous.offlineSince() != null
                ? previous.offlineSince()
                : checkedAt;
        return new HealthSnapshot(
                STATUS_OFFLINE,
                false,
                checkedAt,
                lastOnlineAt,
                offlineSince,
                responseTimeMs,
                consecutiveFailures
        );
    }

    private void applySnapshot(HospitalDicomServerResponse row) {
        if (row == null) {
            return;
        }
        if (row.getIsActive() != null && row.getIsActive() != 1L) {
            row.setHealthStatus(STATUS_INACTIVE);
            row.setHealthOnline(false);
            return;
        }
        HealthSnapshot snapshot = row.getId() == null ? null : snapshots.get(row.getId());
        if (snapshot == null) {
            row.setHealthStatus(STATUS_UNKNOWN);
            row.setHealthOnline(false);
            return;
        }
        row.setHealthStatus(snapshot.status());
        row.setHealthOnline(snapshot.online());
        row.setHealthCheckedAt(formatInstant(snapshot.checkedAt()));
        row.setHealthLastOnlineAt(formatInstant(snapshot.lastOnlineAt()));
        row.setHealthOfflineSince(formatInstant(snapshot.offlineSince()));
        row.setHealthOfflineSeconds(offlineSeconds(snapshot));
        row.setHealthResponseTimeMs(snapshot.responseTimeMs());
    }

    private DicomServerHealthResponse toHealthResponse(HospitalDicomServerResponse server) {
        DicomServerHealthResponse response = new DicomServerHealthResponse();
        response.setPublicKey(server.getPublicKey());
        response.setName(server.getName());
        response.setHospitalPublicKey(server.getHospitalPublicKey());
        response.setHospitalName(server.getHospitalName());
        response.setIpAddress(server.getIpAddress());
        response.setPublicHealthCheckUrl(server.getPublicHealthCheckUrl());
        response.setPort(server.getPort());
        response.setDicomPort(server.getDicomPort());
        response.setAeTitle(server.getAeTitle());
        HealthSnapshot snapshot = server.getId() == null ? null : snapshots.get(server.getId());
        if (snapshot == null) {
            response.setStatus(STATUS_UNKNOWN);
            response.setOnline(false);
            return response;
        }
        response.setStatus(snapshot.status());
        response.setOnline(snapshot.online());
        response.setCheckedAt(formatInstant(snapshot.checkedAt()));
        response.setLastOnlineAt(formatInstant(snapshot.lastOnlineAt()));
        response.setOfflineSince(formatInstant(snapshot.offlineSince()));
        response.setOfflineSeconds(offlineSeconds(snapshot));
        response.setResponseTimeMs(snapshot.responseTimeMs());
        return response;
    }

    private HealthProbeSettings loadSettings() {
        Instant now = Instant.now();
        HealthProbeSettings settings = cachedSettings;
        if (settings != null && now.isBefore(settingsCacheUntil)) {
            return settings;
        }
        synchronized (this) {
            settings = cachedSettings;
            if (settings != null && now.isBefore(settingsCacheUntil)) {
                return settings;
            }
            settings = readSettingsFromDb();
            cachedSettings = settings;
            settingsCacheUntil = now.plusSeconds(5);
            return settings;
        }
    }

    private HealthProbeSettings readSettingsFromDb() {
        try {
            String enabledValue = readSetting(SETTING_HEALTH_ENABLED, "true");
            String intervalValue = readSetting(SETTING_HEALTH_INTERVAL, Integer.toString(DEFAULT_POLL_INTERVAL_SECONDS));
            boolean enabled = !"false".equalsIgnoreCase(enabledValue);
            int interval = normalizePollInterval(parseInteger(intervalValue, DEFAULT_POLL_INTERVAL_SECONDS));
            return new HealthProbeSettings(enabled, interval);
        } catch (Exception error) {
            LOGGER.debug("DICOM server health settings fallback applied: {}", error.getMessage());
            return new HealthProbeSettings(true, DEFAULT_POLL_INTERVAL_SECONDS);
        }
    }

    private String readSetting(String key, String fallback) {
        try {
            String value = jdbcTemplate.queryForObject(
                    "SELECT setting_value FROM pacs_system_settings WHERE setting_key = ?",
                    String.class,
                    key
            );
            String trimmed = trimToNull(value);
            return trimmed == null ? fallback : trimmed;
        } catch (DataAccessException error) {
            return fallback;
        }
    }

    private void upsertSetting(String key, String value, Long modifiedBy) {
        jdbcTemplate.update("""
                INSERT INTO pacs_system_settings (setting_key, setting_value, modified_by, modified_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (setting_key) DO UPDATE SET
                    setting_value = EXCLUDED.setting_value,
                    modified_by = EXCLUDED.modified_by,
                    modified_at = CURRENT_TIMESTAMP
                """, key, value, modifiedBy);
    }

    private static int parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int normalizePollInterval(Integer value) {
        int normalized = value == null ? DEFAULT_POLL_INTERVAL_SECONDS : value;
        if (normalized < MIN_POLL_INTERVAL_SECONDS) {
            return MIN_POLL_INTERVAL_SECONDS;
        }
        return Math.min(normalized, MAX_POLL_INTERVAL_SECONDS);
    }

    private static int normalizeOfflineFailureThreshold(Integer value) {
        int normalized = value == null ? DEFAULT_OFFLINE_FAILURE_THRESHOLD : value;
        if (normalized < 1) {
            return 1;
        }
        return Math.min(normalized, MAX_OFFLINE_FAILURE_THRESHOLD);
    }

    private static long normalizeOfflineGraceMs(Long value) {
        long normalized = value == null ? DEFAULT_OFFLINE_GRACE_MS : value;
        return Math.max(0L, normalized);
    }

    private static boolean isReachableStatus(int statusCode) {
        return (statusCode >= 200 && statusCode < 400) || statusCode == 401 || statusCode == 403;
    }

    private static String buildHealthUrl(HospitalDicomServerResponse server) {
        return buildHealthUrls(server).get(0);
    }

    private static List<String> buildHealthUrls(HospitalDicomServerResponse server) {
        Set<String> urls = new LinkedHashSet<>();
        String dockerAliasHealthUrl = buildDockerAliasHealthUrl(server);
        if (isRunningInContainer()) {
            addHealthUrl(urls, dockerAliasHealthUrl);
        }

        String configuredHealthUrl = trimToNull(server.getPublicHealthCheckUrl());
        if (configuredHealthUrl != null) {
            addHealthUrl(urls, configuredHealthUrl);
        }

        String baseUrl = trimToNull(server.getBaseUrl());
        if (baseUrl == null) {
            String host = trimToNull(server.getIpAddress());
            if (host == null) {
                throw new IllegalArgumentException("DICOM server host is not configured.");
            }
            String scheme = Boolean.TRUE.equals(server.getSslEnabled()) ? "https" : "http";
            int port = server.getPort() == null || server.getPort() <= 0 ? 8042 : server.getPort();
            baseUrl = scheme + "://" + host + ":" + port;
        }
        addHealthUrl(urls, baseUrl);
        if (!isRunningInContainer()) {
            addHealthUrl(urls, dockerAliasHealthUrl);
        }
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("DICOM server host is not configured.");
        }
        return new ArrayList<>(urls);
    }

    private static void addHealthUrl(Set<String> urls, String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null) {
            urls.add(normalizeHealthUrl(trimmed));
        }
    }

    private static String buildDockerAliasHealthUrl(HospitalDicomServerResponse server) {
        String alias = buildDockerNetworkAlias(server);
        if (alias == null) {
            return null;
        }
        String scheme = Boolean.TRUE.equals(server.getSslEnabled()) ? "https" : "http";
        int port = server.getPort() == null || server.getPort() <= 0 ? 8042 : server.getPort();
        return scheme + "://" + alias + ":" + port;
    }

    private static String buildDockerNetworkAlias(HospitalDicomServerResponse server) {
        String hospitalSlug = compactHospitalSlug(server == null ? null : server.getHospitalName());
        String fallbackId = server == null || server.getId() == null ? "" : server.getId().toString();
        String serverText = firstNonBlank(
                server == null ? null : server.getName(),
                "server-" + fallbackId
        ).replaceAll("(?i)\\b(?:udaya[\\s_-]*)?dicom[\\s_-]*server\\b", " ");
        String serverSlug = toSlug(serverText);
        if (DICOM_SERVER_PREFIX.equals(serverSlug) || LEGACY_DICOM_SERVER_PREFIX.equals(serverSlug)) {
            serverSlug = "server-" + fallbackId;
        }
        if (serverSlug.startsWith(hospitalSlug)) {
            return toDockerDnsAlias(DICOM_SERVER_PREFIX + "_" + serverSlug);
        }
        return toDockerDnsAlias(DICOM_SERVER_PREFIX + "_" + hospitalSlug + "_" + serverSlug);
    }

    private static String compactHospitalSlug(String hospitalName) {
        String text = firstNonBlank(hospitalName, "hospital");
        String[] split = text.split("\\s+-\\s+|\\s+/\\s+");
        if (split.length > 0 && trimToNull(split[0]) != null) {
            text = split[0];
        }
        text = text.replaceAll("(?i)\\b(hospital|clinic|medical|center|centre)\\b", " ");
        String slug = toSlug(text);
        return trimToNull(slug) == null ? "hospital" : slug;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String toSlug(String value) {
        String normalized = (value == null ? DICOM_SERVER_PREFIX : value)
                .trim()
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-")
                .toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? DICOM_SERVER_PREFIX : normalized;
    }

    private static String toDockerDnsAlias(String value) {
        return toSlug(value == null ? DICOM_SERVER_PREFIX : value.replace('_', '-'));
    }

    private static String normalizeHealthUrl(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            URI uri = URI.create(normalized);
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                return normalized + "/system";
            }
        } catch (Exception ignored) {
            return normalized + "/system";
        }
        return normalized;
    }

    private String rewriteHealthLoopbackUrlIfNeeded(String healthUrl) {
        if (!loopbackRewriteEnabled || !isRunningInContainer()) {
            return healthUrl;
        }
        String overrideHost = trimToNull(loopbackHostOverride);
        if (overrideHost == null) {
            overrideHost = DEFAULT_LOOPBACK_HOST_OVERRIDE;
        }
        return rewriteLoopbackUrl(healthUrl, overrideHost);
    }

    private static String rewriteLoopbackUrl(String value, String overrideHost) {
        if (trimToNull(value) == null || trimToNull(overrideHost) == null) {
            return value;
        }
        try {
            URI uri = new URI(value);
            if (!isLoopbackHost(uri.getHost())) {
                return value;
            }
            URI rewritten = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    overrideHost.trim(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            );
            return rewritten.toString();
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return value;
        }
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equals(normalized);
    }

    private static boolean isRunningInContainer() {
        try {
            if (Files.exists(Path.of("/.dockerenv"))) {
                return true;
            }
            try (var lines = Files.lines(Path.of("/proc/1/cgroup"))) {
                return lines.anyMatch(line -> {
                    String normalized = line.toLowerCase(Locale.ROOT);
                    return normalized.contains("docker")
                            || normalized.contains("kubepods")
                            || normalized.contains("containerd");
                });
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Long offlineSeconds(HealthSnapshot snapshot) {
        if (snapshot == null || snapshot.online() || snapshot.offlineSince() == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(snapshot.offlineSince(), Instant.now()).getSeconds());
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? null : DATE_TIME_FORMATTER.format(instant);
    }

    private static long elapsedMs(long started) {
        return Math.max(0L, Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    private record HealthSnapshot(
            String status,
            boolean online,
            Instant checkedAt,
            Instant lastOnlineAt,
            Instant offlineSince,
            Long responseTimeMs,
            int consecutiveFailures
    ) {
    }

    private record HealthProbeSettings(
            boolean enabled,
            int pollIntervalSeconds
    ) {
    }
}
