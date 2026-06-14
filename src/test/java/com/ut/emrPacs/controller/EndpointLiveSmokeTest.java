package com.ut.emrPacs.controller;

import com.ut.emrPacs.support.EndpointTestCatalog;
import com.ut.emrPacs.support.EndpointTestCatalog.EndpointSpec;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EndpointLiveSmokeTest {

    private static final String BASE_URL_PROPERTY = "live.api.base-url";
    private static final Set<String> EXPECTED_EMPTY_RESOURCE_404S = Set.of(
            "POST /pacs-result/pacs-result-image-content"
    );

    @Test
    void deployedApiShouldRespondForEveryMappedEndpoint() throws Exception {
        String apiBaseUrl = System.getProperty(BASE_URL_PROPERTY, "").trim();
        assumeTrue(!apiBaseUrl.isBlank(), "Set -D" + BASE_URL_PROPERTY + "=http://127.0.0.1:8080/pacsApi to run the live smoke test");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        Map<Integer, Integer> statuses = new TreeMap<>();
        List<String> unexpected = new ArrayList<>();

        for (EndpointSpec endpoint : EndpointTestCatalog.allEndpoints()) {
            HttpRequest request = buildRequest(apiBaseUrl, endpoint);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            statuses.merge(status, 1, Integer::sum);

            if (!isExpectedLiveSmokeStatus(endpoint, status)) {
                unexpected.add(endpoint.displayName() + " -> " + status);
            }
        }

        assertTrue(
                unexpected.isEmpty(),
                () -> "Unexpected live endpoint responses: " + unexpected + ". Status summary: " + statuses
        );
    }

    private static HttpRequest buildRequest(String apiBaseUrl, EndpointSpec endpoint) {
        String base = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(base + endpoint.concretePath()))
                .timeout(Duration.ofSeconds(15));

        if (endpoint.isMultipart()) {
            String boundary = "----pacs-live-smoke";
            String body = "--" + boundary + "--\r\n";
            return builder
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .method(endpoint.method(), HttpRequest.BodyPublishers.ofString(body))
                    .build();
        }
        if (endpoint.sendsJsonBody()) {
            return builder
                    .header("Content-Type", "application/json")
                    .method(endpoint.method(), HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
        }
        if ("GET".equals(endpoint.method()) || "HEAD".equals(endpoint.method())) {
            return builder.method(endpoint.method(), HttpRequest.BodyPublishers.noBody()).build();
        }
        return builder.method(endpoint.method(), HttpRequest.BodyPublishers.noBody()).build();
    }

    private static boolean isExpectedLiveSmokeStatus(EndpointSpec endpoint, int status) {
        if (status >= 500 || status == 405 || status == 415) {
            return false;
        }
        if (status == 404) {
            return EXPECTED_EMPTY_RESOURCE_404S.contains(endpoint.displayName());
        }
        return true;
    }
}
