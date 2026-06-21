package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerWorklistCreateRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DicomServerClientServiceImplWorklistCrudTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private DicomServerClientServiceImpl dicomServerClientService;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        dicomServerClientService = new DicomServerClientServiceImpl(restTemplate);
    }

    @Test
    void getWorklistByIdShouldUseNormalizedBaseUrlAndBasicAuth() {
        server.expect(once(), requestTo("http://localhost:8042/worklists/wl-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("dicom_server", "dicom_server")))
                .andRespond(withSuccess(worklistJson("wl-1"), MediaType.APPLICATION_JSON));

        var response = dicomServerClientService.getWorklistById(
                "http://localhost:8042/worklists/create",
                "dicom_server",
                "dicom_server",
                "wl-1"
        );

        assertEquals("wl-1", response.getId());
        server.verify();
    }

    @Test
    void updateWorklistByIdShouldPutThenFetchUpdatedWorklist() {
        DicomServerWorklistCreateRequest request = new DicomServerWorklistCreateRequest();
        request.setTags(new DicomServerWorklistCreateRequest.Tags());

        server.expect(once(), requestTo("http://localhost:8042/worklists/wl-2"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("dicom_server", "dicom_server")))
                .andRespond(withSuccess());

        server.expect(once(), requestTo("http://localhost:8042/worklists/wl-2"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("dicom_server", "dicom_server")))
                .andRespond(withSuccess(worklistJson("wl-2"), MediaType.APPLICATION_JSON));

        var response = dicomServerClientService.updateWorklistById(
                "http://localhost:8042",
                "dicom_server",
                "dicom_server",
                "wl-2",
                request
        );

        assertEquals("wl-2", response.getId());
        server.verify();
    }

    @Test
    void deleteWorklistByIdShouldDeleteNormalizedUrl() {
        server.expect(once(), requestTo("http://localhost:8042/worklists/wl-3"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("dicom_server", "dicom_server")))
                .andRespond(withSuccess());

        dicomServerClientService.deleteWorklistById(
                "http://localhost:8042/worklists/create",
                "dicom_server",
                "dicom_server",
                "wl-3"
        );

        server.verify();
    }

    @Test
    void deleteStudyByIdShouldTreatNotFoundAsAlreadyDeleted() {
        server.expect(once(), requestTo("http://localhost:8042/studies/study-missing"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("dicom_server", "dicom_server")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        dicomServerClientService.deleteStudyById(
                "http://localhost:8042",
                "dicom_server",
                "dicom_server",
                "study-missing"
        );

        server.verify();
    }

    @Test
    void proxyDicomWebShouldReturnDicomJsonAsRawBytes() throws Exception {
        String dicomJson = "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3\"]}}]";
        MediaType dicomJsonType = MediaType.parseMediaType("application/dicom+json");
        server.expect(once(), requestTo("http://localhost:8042/dicom-web/studies?StudyInstanceUID=1.2.3"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("dicom_server", "dicom_server")))
                .andRespond(withSuccess(dicomJson, dicomJsonType));

        var response = dicomServerClientService.proxyDicomWeb(
                "http://localhost:8042/dicom-web",
                "dicom_server",
                "dicom_server",
                "/studies?StudyInstanceUID=1.2.3",
                "application/dicom+json"
        );

        assertEquals(dicomJsonType, response.getHeaders().getContentType());
        assertEquals(dicomJson, new String(response.getBody(), StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void proxyDicomWebShouldReturnActualContentLengthForMultipartFrame() throws Exception {
        byte[] frame = "frame-bytes".getBytes(StandardCharsets.UTF_8);
        MediaType frameType = MediaType.parseMediaType("multipart/related; type=\"application/octet-stream; transfer-syntax=1.2.840.10008.1.2.1\"; boundary=abc");
        server.expect(once(), requestTo("http://localhost:8042/dicom-web/studies/1/series/2/instances/3/frames/1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("dicom_server", "dicom_server")))
                .andRespond(withSuccess(frame, frameType));

        var response = dicomServerClientService.proxyDicomWeb(
                "http://localhost:8042/dicom-web",
                "dicom_server",
                "dicom_server",
                "/studies/1/series/2/instances/3/frames/1",
                "multipart/related; type=\"application/octet-stream\""
        );

        assertEquals(frameType, response.getHeaders().getContentType());
        assertEquals(frame.length, response.getHeaders().getContentLength());
        assertArrayEquals(frame, response.getBody());
        server.verify();
    }

    @Test
    void proxyDicomWebShouldRetryTransientBadGatewayAndReturnFrame() throws Exception {
        byte[] frame = "recovered-frame".getBytes(StandardCharsets.UTF_8);
        MediaType frameType = MediaType.parseMediaType("multipart/related; type=\"application/octet-stream\"; boundary=abc");
        String url = "http://localhost:8042/dicom-web/studies/1/series/2/instances/3/frames/1";

        server.expect(once(), requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(once(), requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(frame, frameType));

        var response = dicomServerClientService.proxyDicomWeb(
                "http://localhost:8042/dicom-web",
                null,
                null,
                "/studies/1/series/2/instances/3/frames/1",
                "multipart/related; type=\"application/octet-stream\""
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(frame.length, response.getHeaders().getContentLength());
        assertArrayEquals(frame, response.getBody());
        server.verify();
    }

    @Test
    void proxyDicomWebShouldNotRetryDeterministicNotFound() {
        String url = "http://localhost:8042/dicom-web/studies/1/series/2/instances/missing/frames/1";
        server.expect(once(), requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(
                HttpClientErrorException.NotFound.class,
                () -> dicomServerClientService.proxyDicomWeb(
                        "http://localhost:8042/dicom-web",
                        null,
                        null,
                        "/studies/1/series/2/instances/missing/frames/1",
                        "multipart/related; type=\"application/octet-stream\""
                )
        );
        server.verify();
    }

    @Test
    void proxyDicomWebStreamShouldStreamPayloadAndForwardRange() throws Exception {
        byte[] frame = "streamed-frame".getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> rangeHeader = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        HttpServer streamServer = HttpServer.create(new InetSocketAddress(0), 0);
        streamServer.createContext("/dicom-web/studies/1/series/2/instances/3/frames/1", exchange -> {
            rangeHeader.set(exchange.getRequestHeaders().getFirst(HttpHeaders.RANGE));
            authHeader.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "multipart/related; type=\"application/octet-stream\"; boundary=abc");
            exchange.getResponseHeaders().add(HttpHeaders.ACCEPT_RANGES, "bytes");
            exchange.sendResponseHeaders(206, frame.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(frame);
            }
        });
        streamServer.start();

        try {
            int port = streamServer.getAddress().getPort();
            var response = dicomServerClientService.proxyDicomWebStream(
                    "http://localhost:" + port + "/dicom-web",
                    "dicom_server",
                    "dicom_server",
                    "/studies/1/series/2/instances/3/frames/1",
                    "multipart/related; type=\"application/octet-stream\"",
                    "bytes=0-10",
                    "GET"
            );

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            assertNotNull(response.getBody());
            response.getBody().writeTo(outputStream);

            assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
            assertEquals("bytes=0-10", rangeHeader.get());
            assertEquals(basicAuth("dicom_server", "dicom_server"), authHeader.get());
            assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
            assertArrayEquals(frame, outputStream.toByteArray());
        } finally {
            streamServer.stop(0);
        }
    }

    @Test
    void proxyDicomWebStreamShouldNotThrowWhenViewerCancelsLargeStream() throws Exception {
        byte[] frame = "streamed-frame".getBytes(StandardCharsets.UTF_8);
        HttpServer streamServer = HttpServer.create(new InetSocketAddress(0), 0);
        streamServer.createContext("/dicom-web/studies/1/series/2/instances/3/frames/1", exchange -> {
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "multipart/related; type=\"application/octet-stream\"; boundary=abc");
            exchange.sendResponseHeaders(200, frame.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(frame);
            }
        });
        streamServer.start();

        try {
            int port = streamServer.getAddress().getPort();
            var response = dicomServerClientService.proxyDicomWebStream(
                    "http://localhost:" + port + "/dicom-web",
                    null,
                    null,
                    "/studies/1/series/2/instances/3/frames/1",
                    "multipart/related; type=\"application/octet-stream\"",
                    null,
                    "GET"
            );

            assertNotNull(response.getBody());
            assertDoesNotThrow(() -> response.getBody().writeTo(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    throw new IOException("Broken pipe");
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    throw new IOException("Broken pipe");
                }
            }));
        } finally {
            streamServer.stop(0);
        }
    }

    private static String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String worklistJson(String id) {
        return """
                {
                  "ID": "%s",
                  "Path": "/worklists/%s",
                  "Tags": {
                    "PatientID": "26P0000001",
                    "PatientName": "Soklin Test",
                    "PatientBirthDate": "20260521",
                    "PatientSex": "M",
                    "AccessionNumber": "ACC-%s",
                    "StudyDescription": "CT Chest",
                    "RequestedProcedureID": "ACC-%s",
                    "RequestedProcedureDescription": "CT Chest",
                    "ScheduledProcedureStepSequence": [
                      {
                        "Modality": "CT",
                        "ScheduledStationAETitle": "dicom_server",
                        "ScheduledProcedureStepStartDate": "20260522",
                        "ScheduledProcedureStepStartTime": "090000",
                        "ScheduledProcedureStepDescription": "CT Chest",
                        "ScheduledProcedureStepID": "ACC-%s"
                      }
                    ]
                  }
                }
                """.formatted(id, id, id, id, id);
    }
}
