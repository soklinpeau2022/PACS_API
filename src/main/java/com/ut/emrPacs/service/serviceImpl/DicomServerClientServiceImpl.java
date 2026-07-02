package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerFindRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerInstanceUploadResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerSeriesResponse;
import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerWorklistCreateRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerStudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistCreateResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistResponse;
import com.ut.emrPacs.service.service.DicomServerClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Flow;

@Service
public class DicomServerClientServiceImpl implements DicomServerClientService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomServerClientServiceImpl.class);

    private final RestTemplate restTemplate;
    private final HttpClient uploadHttpClient;
    private final ObjectMapper objectMapper;
    private final Duration uploadTimeout;
    private final boolean loopbackRewriteEnabled;
    private final boolean runningInContainer;
    private final String loopbackHostOverride;

    // DICOMweb frame/pixel retrieval is idempotent (GET/HEAD). The archive can transiently
    // fail or reset connections when it is momentarily saturated (e.g. its HTTP worker threads
    // are blocked on slow modality C-FIND/C-MOVE associations). Retry those transient failures a
    // few times with a short backoff so a single hiccup does not surface to the viewer as a 502.
    private static final int DICOMWEB_PROXY_MAX_ATTEMPTS = 3;
    private static final long DICOMWEB_PROXY_RETRY_BACKOFF_MS = 200L;
    private static final int DICOMWEB_STREAM_BUFFER_BYTES = 1024 * 1024;
    private static final int UPLOAD_ERROR_BODY_MAX_CHARS = 400;
    private static final String DEFAULT_LOOPBACK_HOST_OVERRIDE = "";

    DicomServerClientServiceImpl(RestTemplate restTemplate) {
        this(restTemplate, new ObjectMapper(), 10000, 7200000, true, DEFAULT_LOOPBACK_HOST_OVERRIDE, isRunningInContainer());
    }

    DicomServerClientServiceImpl(RestTemplate restTemplate, boolean runningInContainer) {
        this(restTemplate, new ObjectMapper(), 10000, 7200000, true, DEFAULT_LOOPBACK_HOST_OVERRIDE, runningInContainer);
    }

    DicomServerClientServiceImpl(RestTemplate restTemplate, boolean runningInContainer, String loopbackHostOverride) {
        this(restTemplate, new ObjectMapper(), 10000, 7200000, true, loopbackHostOverride, runningInContainer);
    }

    @Autowired
    public DicomServerClientServiceImpl(
            RestTemplate restTemplate,
            @Value("${pacs.dicom-server.client.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${pacs.dicom-server.client.read-timeout-ms:7200000}") int readTimeoutMs,
            @Value("${pacs.dicom-server.client.rewrite-loopback-in-container:true}") boolean loopbackRewriteEnabled,
            @Value("${pacs.dicom-server.client.loopback-host-override:}") String loopbackHostOverride
    ) {
        this(restTemplate, new ObjectMapper(), connectTimeoutMs, readTimeoutMs, loopbackRewriteEnabled, loopbackHostOverride, isRunningInContainer());
    }

    private DicomServerClientServiceImpl(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            int connectTimeoutMs,
            int readTimeoutMs,
            boolean loopbackRewriteEnabled,
            String loopbackHostOverride,
            boolean runningInContainer
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.uploadTimeout = Duration.ofMillis(Math.max(1000, readTimeoutMs));
        this.loopbackRewriteEnabled = loopbackRewriteEnabled;
        this.runningInContainer = runningInContainer;
        this.loopbackHostOverride = firstNonBlank(loopbackHostOverride, DEFAULT_LOOPBACK_HOST_OVERRIDE);
        this.uploadHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, connectTimeoutMs)))
                .build();
    }

    @Override
    public DicomServerWorklistCreateResponse postToDicomServerWorklist(DicomServerWorklistCreateRequest request) {
        return postToDicomServerWorklist(resolveDefaultWorklistCreateUrl(), null, null, request);
    }

    @Override
    public DicomServerWorklistCreateResponse postToDicomServerWorklist(String worklistUrl, String username, String password, DicomServerWorklistCreateRequest request) {
        HttpEntity<DicomServerWorklistCreateRequest> entity = new HttpEntity<>(request, buildHeaders(username, password));
        ResponseEntity<DicomServerWorklistCreateResponse> response = restTemplate.postForEntity(
                normalizeWorklistCreateUrl(worklistUrl),
                entity,
                DicomServerWorklistCreateResponse.class
        );
        return response.getBody();
    }

    @Override
    public DicomServerInstanceUploadResponse uploadInstance(String baseUrl, String username, String password, Resource dicomResource, long contentLength) {
        String url = normalizeDicomServerRestBaseUrl(baseUrl) + "/instances";
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofInputStream(() -> {
            try {
                return dicomResource.getInputStream();
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        });
        if (contentLength >= 0) {
            bodyPublisher = new KnownLengthBodyPublisher(bodyPublisher, contentLength);
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                .timeout(uploadTimeout)
                .header(HttpHeaders.CONTENT_TYPE, "application/dicom")
                .POST(bodyPublisher);
        applyBasicAuth(requestBuilder, username, password);

        HttpResponse<String> response;
        try {
            response = uploadHttpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException error) {
            throw new ResourceAccessException("DICOM server is unreachable.", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DICOM upload was interrupted.", error);
        } catch (UncheckedIOException error) {
            throw new ResourceAccessException("Unable to read DICOM upload stream.", error.getCause());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            DicomServerInstanceUploadResponse alreadyStoredResponse = readAlreadyStoredUploadResponse(response.body());
            if (alreadyStoredResponse != null) {
                return alreadyStoredResponse;
            }
            throw new IllegalStateException("DICOM server upload failed with HTTP " + response.statusCode() + uploadErrorBodySuffix(response.body()) + ".");
        }
        try {
            return objectMapper.readValue(response.body(), DicomServerInstanceUploadResponse.class);
        } catch (IOException error) {
            throw new IllegalStateException("DICOM server upload response was not readable.", error);
        }
    }

    @Override
    public void deleteInstanceById(
            String baseUrl,
            String username,
            String password,
            String instanceId
    ) {
        String normalizedInstanceId = instanceId == null ? "" : instanceId.trim();
        if (normalizedInstanceId.isEmpty()) {
            throw new IllegalArgumentException("DICOM server instance ID is required.");
        }
        String url = normalizeDicomServerRestBaseUrl(baseUrl) + "/instances/" + normalizedInstanceId;
        try {
            restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    new HttpEntity<>(buildHeaders(username, password)),
                    Void.class
            );
        } catch (HttpClientErrorException.NotFound notFound) {
            // Rollback deletion is idempotent.
        }
    }

    @Override
    public DicomServerWorklistResponse getWorklistById(String worklistId) {
        return getWorklistById(resolveDefaultDicomServerBaseUrl(), null, null, worklistId);
    }

    @Override
    public DicomServerWorklistResponse getWorklistById(String baseUrl, String username, String password, String worklistId) {
        String url = buildWorklistItemUrl(baseUrl, worklistId);
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(username, password));
        ResponseEntity<DicomServerWorklistResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                DicomServerWorklistResponse.class
        );
        return response.getBody();
    }

    @Override
    public DicomServerWorklistResponse updateWorklistById(String worklistId, DicomServerWorklistCreateRequest request) {
        return updateWorklistById(resolveDefaultDicomServerBaseUrl(), null, null, worklistId, request);
    }

    @Override
    public DicomServerWorklistResponse updateWorklistById(String baseUrl, String username, String password, String worklistId, DicomServerWorklistCreateRequest request) {
        String url = buildWorklistItemUrl(baseUrl, worklistId);
        HttpEntity<DicomServerWorklistCreateRequest> entity = new HttpEntity<>(request, buildHeaders(username, password));
        restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
        return getWorklistById(baseUrl, username, password, worklistId);
    }

    @Override
    public void deleteWorklistById(String worklistId) {
        deleteWorklistById(resolveDefaultDicomServerBaseUrl(), null, null, worklistId);
    }

    @Override
    public void deleteWorklistById(String baseUrl, String username, String password, String worklistId) {
        String url = buildWorklistItemUrl(baseUrl, worklistId);
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(username, password));
        restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
    }

    @Override
    public List<String> findStudyIdsByAccessionNumber(DicomServerFindRequest request) {
        return findStudyIdsByAccessionNumber(resolveDefaultDicomServerBaseUrl(), null, null, request);
    }

    @Override
    public List<String> findStudyIdsByAccessionNumber(String baseUrl, String username, String password, DicomServerFindRequest request) {
        String findUrl = buildFindUrl(baseUrl);
        ResponseEntity<List<String>> response = restTemplate.exchange(
                findUrl,
                HttpMethod.POST,
                new HttpEntity<>(request, buildHeaders(username, password)),
                new ParameterizedTypeReference<>() {}
        );
        List<String> body = response.getBody();
        return body == null ? Collections.emptyList() : body;
    }

    @Override
    public DicomServerStudyResponse getStudyById(String studyId) {
        return getStudyById(resolveDefaultDicomServerBaseUrl(), null, null, studyId);
    }

    @Override
    public DicomServerStudyResponse getStudyById(String baseUrl, String username, String password, String studyId) {
        String url = normalizeDicomServerRestBaseUrl(baseUrl) + "/studies/" + studyId;
        ResponseEntity<DicomServerStudyResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders(username, password)),
                DicomServerStudyResponse.class
        );
        return response.getBody();
    }

    @Override
    public void deleteStudyById(String studyId) {
        deleteStudyById(resolveDefaultDicomServerBaseUrl(), null, null, studyId);
    }

    @Override
    public void deleteStudyById(String baseUrl, String username, String password, String studyId) {
        String normalizedStudyId = studyId == null ? "" : studyId.trim();
        if (normalizedStudyId.isEmpty()) {
            throw new IllegalArgumentException("DICOM server study ID is required.");
        }
        String url = normalizeDicomServerRestBaseUrl(baseUrl) + "/studies/" + normalizedStudyId;
        try {
            restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    new HttpEntity<>(buildHeaders(username, password)),
                    Void.class
            );
        } catch (HttpClientErrorException.NotFound notFound) {
            // DELETE is idempotent for retention cleanup: a missing archive study is already gone.
        }
    }

    @Override
    public List<DicomServerSeriesResponse> getSeriesByStudyId(String studyId) {
        return getSeriesByStudyId(resolveDefaultDicomServerBaseUrl(), null, null, studyId);
    }

    @Override
    public List<DicomServerSeriesResponse> getSeriesByStudyId(String baseUrl, String username, String password, String studyId) {
        String url = normalizeDicomServerRestBaseUrl(baseUrl) + "/studies/" + studyId + "/series";
        ResponseEntity<List<DicomServerSeriesResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders(username, password)),
                new ParameterizedTypeReference<>() {}
        );
        List<DicomServerSeriesResponse> body = response.getBody();
        return body == null ? Collections.emptyList() : body;
    }

    @Override
    public ResponseEntity<byte[]> getInstancePreview(String instanceId) {
        return getInstancePreview(resolveDefaultDicomServerBaseUrl(), null, null, instanceId);
    }

    @Override
    public ResponseEntity<byte[]> getInstancePreview(String baseUrl, String username, String password, String instanceId) {
        String url = normalizeDicomServerRestBaseUrl(baseUrl) + "/instances/" + instanceId + "/preview";
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(username, password));
        return restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                byte[].class
        );
    }

    @Override
    public ResponseEntity<byte[]> proxyDicomWeb(
            String dicomwebBaseUrl,
            String username,
            String password,
            String pathAndQuery,
            String acceptHeader
    ) {
        String url = normalizeDicomWebBaseUrl(dicomwebBaseUrl) + normalizePathAndQuery(pathAndQuery);
        HttpHeaders requestHeaders = buildHeaders(username, password);
        requestHeaders.remove(HttpHeaders.CONTENT_TYPE);
        if (acceptHeader != null && !acceptHeader.isBlank()) {
            requestHeaders.set(HttpHeaders.ACCEPT, acceptHeader.trim());
        }

        ResponseEntity<byte[]> upstream = exchangeDicomWebWithRetry(url, new HttpEntity<>(requestHeaders));

        HttpHeaders responseHeaders = new HttpHeaders();
        MediaType contentType = upstream.getHeaders().getContentType();
        if (contentType != null) {
            responseHeaders.setContentType(contentType);
        }
        copyHeader(upstream, responseHeaders, HttpHeaders.CONTENT_RANGE);
        copyHeader(upstream, responseHeaders, HttpHeaders.ACCEPT_RANGES);
        copyHeader(upstream, responseHeaders, HttpHeaders.ETAG);
        copyHeader(upstream, responseHeaders, HttpHeaders.LAST_MODIFIED);
        responseHeaders.setCacheControl("no-store");
        responseHeaders.add("X-Content-Type-Options", "nosniff");

        byte[] body = upstream.getBody();
        byte[] responseBody = body == null ? new byte[0] : body;
        responseHeaders.setContentLength(responseBody.length);
        return new ResponseEntity<>(responseBody, responseHeaders, upstream.getStatusCode());
    }

    @Override
    public ResponseEntity<StreamingResponseBody> proxyDicomWebStream(
            String dicomwebBaseUrl,
            String username,
            String password,
            String pathAndQuery,
            String acceptHeader,
            String rangeHeader,
            String requestMethod
    ) {
        String url = normalizeDicomWebBaseUrl(dicomwebBaseUrl) + normalizePathAndQuery(pathAndQuery);
        String resolvedMethod = normalizeDicomWebProxyMethod(requestMethod);
        HttpRequest request = buildDicomWebStreamRequest(
                url,
                username,
                password,
                acceptHeader,
                rangeHeader,
                resolvedMethod
        );
        HttpResponse<InputStream> upstream = exchangeDicomWebStreamWithRetry(request);
        HttpHeaders responseHeaders = buildDicomWebStreamResponseHeaders(upstream);

        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = upstream.body()) {
                if (!HttpMethod.HEAD.matches(resolvedMethod)) {
                    copyDicomWebStream(inputStream, outputStream);
                }
            } catch (IOException | IllegalStateException error) {
                // The OHIF viewer may cancel/restart large frame or metadata requests while
                // building 3D context. After streaming starts, throwing here makes Spring try
                // to render an error response on an already-active async response, which can
                // surface as Tomcat header NPEs. Let the client retry the interrupted request.
                if (isExpectedStreamStop(error)) {
                    LOGGER.debug("DICOMweb stream stopped by client/viewer: {}", error.toString());
                } else {
                    LOGGER.warn("DICOMweb stream stopped before completion: {}", error.toString());
                }
            }
        };
        return new ResponseEntity<>(body, responseHeaders, HttpStatusCode.valueOf(upstream.statusCode()));
    }

    private static void copyDicomWebStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[DICOMWEB_STREAM_BUFFER_BYTES];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
    }

    private static boolean isExpectedStreamStop(Throwable error) {
        if (error == null) {
            return true;
        }
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(java.util.Locale.ROOT);
        String type = error.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return message.contains("broken pipe")
                || message.contains("connection reset")
                || message.contains("forcibly closed")
                || message.contains("stream is closed")
                || message.contains("clientabort")
                || type.contains("clientabort");
    }

    private ResponseEntity<byte[]> exchangeDicomWebWithRetry(String url, HttpEntity<?> entity) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= DICOMWEB_PROXY_MAX_ATTEMPTS; attempt++) {
            try {
                return restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            } catch (HttpClientErrorException error) {
                // 4xx is a deterministic client error (not found, forbidden, etc.); retrying will not help.
                throw error;
            } catch (HttpServerErrorException | ResourceAccessException error) {
                lastError = error;
                if (attempt < DICOMWEB_PROXY_MAX_ATTEMPTS) {
                    sleepQuietly(DICOMWEB_PROXY_RETRY_BACKOFF_MS * attempt);
                }
            }
        }
        throw lastError;
    }

    private HttpRequest buildDicomWebStreamRequest(
            String url,
            String username,
            String password,
            String acceptHeader,
            String rangeHeader,
            String requestMethod
    ) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                .timeout(uploadTimeout);
        applyBasicAuth(requestBuilder, username, password);
        if (acceptHeader != null && !acceptHeader.isBlank()) {
            requestBuilder.header(HttpHeaders.ACCEPT, acceptHeader.trim());
        }
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            requestBuilder.header(HttpHeaders.RANGE, rangeHeader.trim());
        }
        if (HttpMethod.HEAD.matches(requestMethod)) {
            requestBuilder.method(HttpMethod.HEAD.name(), HttpRequest.BodyPublishers.noBody());
        } else {
            requestBuilder.GET();
        }
        return requestBuilder.build();
    }

    private HttpResponse<InputStream> exchangeDicomWebStreamWithRetry(HttpRequest request) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= DICOMWEB_PROXY_MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<InputStream> response = uploadHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 200 && status < 400) {
                    return response;
                }
                RuntimeException statusError = dicomWebStatusException(response);
                closeQuietly(response.body());
                if (status >= 400 && status < 500) {
                    throw statusError;
                }
                lastError = statusError;
                if (attempt < DICOMWEB_PROXY_MAX_ATTEMPTS) {
                    sleepQuietly(DICOMWEB_PROXY_RETRY_BACKOFF_MS * attempt);
                }
            } catch (IOException error) {
                lastError = new ResourceAccessException("DICOM server is unreachable.", error);
                if (attempt < DICOMWEB_PROXY_MAX_ATTEMPTS) {
                    sleepQuietly(DICOMWEB_PROXY_RETRY_BACKOFF_MS * attempt);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while proxying DICOMweb request", error);
            }
        }
        throw lastError;
    }

    private static RuntimeException dicomWebStatusException(HttpResponse<?> response) {
        HttpStatusCode statusCode = HttpStatusCode.valueOf(response.statusCode());
        if (statusCode.is4xxClientError()) {
            return HttpClientErrorException.create(statusCode, "", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
        }
        return HttpServerErrorException.create(statusCode, "", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
    }

    private static HttpHeaders buildDicomWebStreamResponseHeaders(HttpResponse<?> upstream) {
        HttpHeaders responseHeaders = new HttpHeaders();
        upstream.headers().firstValue(HttpHeaders.CONTENT_TYPE).ifPresent(contentType -> {
            if (!isSafeProxyHeaderValue(contentType)) {
                return;
            }
            try {
                responseHeaders.setContentType(MediaType.parseMediaType(contentType));
            } catch (IllegalArgumentException ignored) {
                responseHeaders.add(HttpHeaders.CONTENT_TYPE, contentType);
            }
        });
        copyHeader(upstream, responseHeaders, HttpHeaders.CONTENT_LENGTH);
        copyHeader(upstream, responseHeaders, HttpHeaders.CONTENT_RANGE);
        copyHeader(upstream, responseHeaders, HttpHeaders.ACCEPT_RANGES);
        copyHeader(upstream, responseHeaders, HttpHeaders.ETAG);
        copyHeader(upstream, responseHeaders, HttpHeaders.LAST_MODIFIED);
        responseHeaders.setCacheControl("no-store");
        responseHeaders.add("X-Content-Type-Options", "nosniff");
        responseHeaders.add("X-Accel-Buffering", "no");
        return responseHeaders;
    }

    private static String normalizeDicomWebProxyMethod(String requestMethod) {
        return HttpMethod.HEAD.matches(requestMethod) ? HttpMethod.HEAD.name() : HttpMethod.GET.name();
    }

    private static void closeQuietly(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (IOException ignored) {
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying DICOMweb proxy request", interrupted);
        }
    }

    private static void copyHeader(ResponseEntity<?> source, HttpHeaders target, String headerName) {
        List<String> values = source.getHeaders().get(headerName);
        if (values != null && !values.isEmpty()) {
            for (String value : values) {
                if (isSafeProxyHeaderValue(value)) {
                    target.add(headerName, value);
                }
            }
        }
    }

    private static void copyHeader(HttpResponse<?> source, HttpHeaders target, String headerName) {
        List<String> values = source.headers().allValues(headerName);
        if (values != null && !values.isEmpty()) {
            for (String value : values) {
                if (isSafeProxyHeaderValue(value)) {
                    target.add(headerName, value);
                }
            }
        }
    }

    private static boolean isSafeProxyHeaderValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\r' || ch == '\n' || ch == 0 || (ch < 32 && ch != '\t')) {
                return false;
            }
        }
        return true;
    }

    private String normalizePathAndQuery(String pathAndQuery) {
        String normalized = pathAndQuery == null ? "" : pathAndQuery.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private HttpHeaders buildHeaders() {
        return buildHeaders(null, null);
    }

    private HttpHeaders buildHeaders(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String resolvedUsername = username != null && !username.isBlank() ? username.trim() : null;
        String resolvedPassword = password != null && !password.isBlank() ? password.trim() : null;
        if (resolvedUsername != null && !resolvedUsername.isBlank()
                && resolvedPassword != null && !resolvedPassword.isBlank()) {
            headers.setBasicAuth(resolvedUsername, resolvedPassword);
        }
        return headers;
    }

    private void applyBasicAuth(HttpRequest.Builder requestBuilder, String username, String password) {
        String resolvedUsername = username != null && !username.isBlank() ? username.trim() : null;
        String resolvedPassword = password != null && !password.isBlank() ? password.trim() : null;
        if (resolvedUsername == null || resolvedPassword == null) {
            return;
        }
        String token = Base64.getEncoder().encodeToString((resolvedUsername + ":" + resolvedPassword).getBytes(StandardCharsets.UTF_8));
        requestBuilder.header(HttpHeaders.AUTHORIZATION, "Basic " + token);
    }

    private String buildWorklistItemUrl(String baseUrl, String worklistId) {
        String normalizedBaseUrl = normalizeWorklistBaseUrl(baseUrl);
        return normalizedBaseUrl + "/worklists/" + worklistId;
    }

    private String normalizeWorklistCreateUrl(String worklistUrl) {
        String normalized = trimToNull(worklistUrl);
        if (normalized == null) {
            return resolveDefaultWorklistCreateUrl();
        }
        normalized = trimTrailingSlashes(stripFragment(normalized));
        if (normalized.endsWith("/worklists/create")) {
            return rewriteServerSideLoopbackBaseUrl(normalized);
        }
        if (normalized.endsWith("/worklists")) {
            return rewriteServerSideLoopbackBaseUrl(normalized + "/create");
        }
        return normalizeDicomServerRestBaseUrl(normalized) + "/worklists/create";
    }

    private String normalizeWorklistBaseUrl(String baseUrl) {
        String normalized = trimToNull(baseUrl);
        if (normalized == null) {
            return resolveDefaultDicomServerBaseUrl();
        }
        normalized = trimTrailingSlashes(stripFragment(normalized));
        if (normalized.endsWith("/worklists/create")) {
            normalized = normalized.substring(0, normalized.length() - "/worklists/create".length());
        } else if (normalized.endsWith("/worklists")) {
            normalized = normalized.substring(0, normalized.length() - "/worklists".length());
        }
        return normalizeDicomServerRestBaseUrl(normalized);
    }

    private String normalizeDicomServerRestBaseUrl(String baseUrl) {
        String normalized = trimToNull(baseUrl);
        if (normalized == null) {
            normalized = resolveDefaultDicomServerBaseUrl();
        }
        normalized = stripOrthancUiPath(trimTrailingSlashes(stripFragment(normalized)));
        return rewriteServerSideLoopbackBaseUrl(normalized);
    }

    private String normalizeDicomWebBaseUrl(String baseUrl) {
        String normalized = trimToNull(baseUrl);
        if (normalized == null) {
            normalized = resolveDefaultDicomServerBaseUrl() + "/dicom-web";
        }
        normalized = trimTrailingSlashes(stripFragment(normalized));
        return rewriteServerSideLoopbackBaseUrl(normalized);
    }

    private String resolveDefaultDicomServerBaseUrl() {
        throw new IllegalStateException("DICOM server base URL is required for this operation.");
    }

    private String resolveDefaultWorklistCreateUrl() {
        return normalizeDicomServerRestBaseUrl(resolveDefaultDicomServerBaseUrl()) + "/worklists/create";
    }

    private String buildFindUrl(String baseUrl) {
        return normalizeDicomServerRestBaseUrl(baseUrl) + "/tools/find";
    }

    private String rewriteServerSideLoopbackBaseUrl(String baseUrl) {
        if (!loopbackRewriteEnabled || !runningInContainer || trimToNull(loopbackHostOverride) == null) {
            return baseUrl;
        }
        try {
            URI uri = new URI(baseUrl);
            String host = uri.getHost();
            if (!isLoopbackHost(host)) {
                return baseUrl;
            }
            URI rewritten = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    loopbackHostOverride,
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            );
            LOGGER.debug("Rewriting DICOM server loopback URL for container access: {} -> {}", baseUrl, rewritten);
            return trimTrailingSlashes(rewritten.toString());
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return baseUrl;
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

    private static String stripOrthancUiPath(String baseUrl) {
        try {
            URI uri = new URI(baseUrl);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return trimTrailingSlashes(baseUrl);
            }
            String normalizedPath = trimTrailingSlashes(path);
            String lowerPath = normalizedPath.toLowerCase(Locale.ROOT);
            if (!lowerPath.equals("/ui")
                    && !lowerPath.startsWith("/ui/")
                    && !lowerPath.equals("/app")
                    && !lowerPath.startsWith("/app/")) {
                return trimTrailingSlashes(baseUrl);
            }
            URI root = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), "", null, null);
            return trimTrailingSlashes(root.toString());
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            String lower = baseUrl.toLowerCase(Locale.ROOT);
            int uiIndex = lower.indexOf("/ui/");
            if (uiIndex < 0 && lower.endsWith("/ui")) {
                uiIndex = lower.length() - "/ui".length();
            }
            if (uiIndex > 0) {
                return trimTrailingSlashes(baseUrl.substring(0, uiIndex));
            }
            return trimTrailingSlashes(baseUrl);
        }
    }

    private static String stripFragment(String url) {
        int fragmentIndex = url.indexOf('#');
        return fragmentIndex >= 0 ? url.substring(0, fragmentIndex) : url;
    }

    private static String trimTrailingSlashes(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
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
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static String uploadErrorBodySuffix(String body) {
        String normalized = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.length() > UPLOAD_ERROR_BODY_MAX_CHARS) {
            normalized = normalized.substring(0, UPLOAD_ERROR_BODY_MAX_CHARS) + "...";
        }
        return ": " + normalized;
    }

    private DicomServerInstanceUploadResponse readAlreadyStoredUploadResponse(String body) {
        String normalized = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (!normalized.contains("alreadystored") && !normalized.contains("already stored")) {
            return null;
        }
        try {
            DicomServerInstanceUploadResponse response = objectMapper.readValue(body, DicomServerInstanceUploadResponse.class);
            if (response.getStatus() == null || response.getStatus().isBlank()) {
                response.setStatus("AlreadyStored");
            }
            return response;
        } catch (Exception ignored) {
            DicomServerInstanceUploadResponse response = new DicomServerInstanceUploadResponse();
            response.setStatus("AlreadyStored");
            return response;
        }
    }

    private record KnownLengthBodyPublisher(HttpRequest.BodyPublisher delegate, long contentLength)
            implements HttpRequest.BodyPublisher {
        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            delegate.subscribe(subscriber);
        }
    }
}
