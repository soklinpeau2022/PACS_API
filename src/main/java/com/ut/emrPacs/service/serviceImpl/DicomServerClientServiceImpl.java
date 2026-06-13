package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerFindRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerSeriesResponse;
import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerWorklistCreateRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerStudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistCreateResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistResponse;
import com.ut.emrPacs.service.service.DicomServerClientService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Collections;
import java.util.List;

@Service
public class DicomServerClientServiceImpl implements DicomServerClientService {

    private final RestTemplate restTemplate;

    public DicomServerClientServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public DicomServerWorklistCreateResponse postToDicomServerWorklist(DicomServerWorklistCreateRequest request) {
        return postToDicomServerWorklist(resolveDefaultWorklistCreateUrl(), null, null, request);
    }

    @Override
    public DicomServerWorklistCreateResponse postToDicomServerWorklist(String worklistUrl, String username, String password, DicomServerWorklistCreateRequest request) {
        HttpEntity<DicomServerWorklistCreateRequest> entity = new HttpEntity<>(request, buildHeaders(username, password));
        ResponseEntity<DicomServerWorklistCreateResponse> response = restTemplate.postForEntity(
                worklistUrl,
                entity,
                DicomServerWorklistCreateResponse.class
        );
        return response.getBody();
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
        String url = normalizeDicomServerBaseUrl(baseUrl) + "/studies/" + studyId;
        ResponseEntity<DicomServerStudyResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders(username, password)),
                DicomServerStudyResponse.class
        );
        return response.getBody();
    }

    @Override
    public List<DicomServerSeriesResponse> getSeriesByStudyId(String studyId) {
        return getSeriesByStudyId(resolveDefaultDicomServerBaseUrl(), null, null, studyId);
    }

    @Override
    public List<DicomServerSeriesResponse> getSeriesByStudyId(String baseUrl, String username, String password, String studyId) {
        String url = normalizeDicomServerBaseUrl(baseUrl) + "/studies/" + studyId + "/series";
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
        String url = normalizeDicomServerBaseUrl(baseUrl) + "/instances/" + instanceId + "/preview";
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(username, password));
        return restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                byte[].class
        );
    }

    @Override
    public ResponseEntity<StreamingResponseBody> proxyDicomWeb(
            String dicomwebBaseUrl,
            String username,
            String password,
            String pathAndQuery,
            String acceptHeader
    ) {
        String url = normalizeDicomServerBaseUrl(dicomwebBaseUrl) + normalizePathAndQuery(pathAndQuery);
        HttpHeaders requestHeaders = buildHeaders(username, password);
        requestHeaders.remove(HttpHeaders.CONTENT_TYPE);
        if (acceptHeader != null && !acceptHeader.isBlank()) {
            requestHeaders.set(HttpHeaders.ACCEPT, acceptHeader.trim());
        }

        ResponseEntity<byte[]> upstream = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(requestHeaders),
                byte[].class
        );

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

        copyHeader(upstream, responseHeaders, HttpHeaders.CONTENT_LENGTH);
        byte[] body = upstream.getBody();
        byte[] responseBody = body == null ? new byte[0] : body;
        StreamingResponseBody stream = outputStream -> outputStream.write(responseBody);
        return new ResponseEntity<>(stream, responseHeaders, upstream.getStatusCode());
    }

    private static void copyHeader(ResponseEntity<?> source, HttpHeaders target, String headerName) {
        List<String> values = source.getHeaders().get(headerName);
        if (values != null && !values.isEmpty()) {
            target.put(headerName, values);
        }
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

    private String buildWorklistItemUrl(String baseUrl, String worklistId) {
        String normalizedBaseUrl = normalizeWorklistBaseUrl(baseUrl);
        return normalizedBaseUrl + "/worklists/" + worklistId;
    }

    private String normalizeWorklistBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (normalized.endsWith("/worklists/create")) {
            normalized = normalized.substring(0, normalized.length() - "/worklists/create".length());
        } else if (normalized.endsWith("/worklists")) {
            normalized = normalized.substring(0, normalized.length() - "/worklists".length());
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? resolveDefaultDicomServerBaseUrl() : normalized;
    }

    private String normalizeDicomServerBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? resolveDefaultDicomServerBaseUrl() : normalized;
    }

    private String resolveDefaultDicomServerBaseUrl() {
        throw new IllegalStateException("DICOM server base URL is required for this operation.");
    }

    private String resolveDefaultWorklistCreateUrl() {
        return resolveDefaultDicomServerBaseUrl() + "/worklists/create";
    }

    private String buildFindUrl(String baseUrl) {
        return normalizeDicomServerBaseUrl(baseUrl) + "/tools/find";
    }
}
