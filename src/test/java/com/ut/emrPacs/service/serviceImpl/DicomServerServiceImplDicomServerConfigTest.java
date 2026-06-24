package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.mapper.auth.OAuth2ClientMapper;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomRoutingConfigResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalModalityServerRouteResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.DicomServerConfigBuildResponse;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalDicomServerRequestUpdate;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.users.User;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DicomServerServiceImplDicomServerConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildDicomServerConfigShouldUseAuthorizationPluginForDirectViewerAccess() {
        DicomServerServiceImpl service = new DicomServerServiceImpl();
        ReflectionTestUtils.setField(service, "apiAuthUrl", "http://localhost:8080/pacsApi");
        HospitalModalityServerRouteResponse route = secureRoute();
        route.setAuthorizationEnabled(false);

        Map<String, Object> config = ReflectionTestUtils.invokeMethod(
                service,
                "buildDicomServerConfig",
                new HospitalDicomRoutingConfigResponse(),
                route,
                List.of(route)
        );

        assertEquals(Boolean.TRUE, config.get("HttpServerEnabled"));
        assertEquals(Boolean.TRUE, config.get("RemoteAccessAllowed"));
        assertEquals(Boolean.TRUE, config.get("AuthenticationEnabled"));
        assertEquals(Map.of("dicom_server", "secret-123"), (Map<String, String>) config.get("RegisteredUsers"));
        Map<String, Object> authorization = (Map<String, Object>) config.get("Authorization");
        assertEquals(Boolean.TRUE, authorization.get("Enabled"));
        assertEquals("http://localhost:8080/pacsApi/worklist/viewer-dicom-web-authorize", authorization.get("WebServiceTokenValidationUrl"));
        assertEquals("http://localhost:8080/pacsApi/worklist/viewer-dicom-web-decode", authorization.get("WebServiceTokenDecoderUrl"));
        assertEquals("http://localhost:8080/pacsApi/worklist/viewer-dicom-web-profile", authorization.get("WebServiceUserProfileUrl"));
        assertEquals(List.of("Authorization"), authorization.get("TokenHttpHeaders"));
        assertEquals(List.of("token"), authorization.get("TokenGetArguments"));
        assertEquals(List.of("ohif"), authorization.get("StandardConfigurations"));
        assertEquals(List.of("/system", "/instances", "/ui/app", "/ui/app/", "/ui/app/index.html"), authorization.get("UncheckedResources"));
        assertEquals(List.of("/ui/app/"), authorization.get("UncheckedFolders"));
        assertEquals("studies", authorization.get("CheckedLevel"));
        assertFalse(authorization.containsKey("ExtraPermissions"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildResponseRedactionShouldRemovePlainSecretsFromApiPayload() {
        DicomServerServiceImpl service = new DicomServerServiceImpl();
        DicomServerConfigBuildResponse response = new DicomServerConfigBuildResponse();
        response.setConfig(new LinkedHashMap<>(Map.of(
                "AuthenticationEnabled", true,
                "RegisteredUsers", new LinkedHashMap<>(Map.of("dicom_server", "secret-123"))
        )));
        response.setEnvironmentContent("dicom_server_CALLBACK_CLIENT_SECRET=plain-secret");
        response.setCallbackScriptContent("script");
        response.setSetupContent("setup");

        ReflectionTestUtils.invokeMethod(service, "redactDicomServerBuildResponse", response);

        assertNull(response.getEnvironmentContent());
        assertNull(response.getCallbackScriptContent());
        assertNull(response.getSetupContent());
        Map<String, String> users = (Map<String, String>) response.getConfig().get("RegisteredUsers");
        assertEquals("********", users.get("dicom_server"));
    }

    @Test
    void healthcheckShouldUseBasicAuthWithoutRawPassword() {
        DicomServerServiceImpl service = new DicomServerServiceImpl();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("AuthenticationEnabled", true);
        config.put("RegisteredUsers", new LinkedHashMap<>(Map.of("dicom_server", "secret-123")));

        String command = ReflectionTestUtils.invokeMethod(service, "buildDicomServerHealthcheckCommand", config);

        assertTrue(command.contains("Authorization"));
        assertTrue(command.contains("Basic "));
        assertTrue(command.contains("/etc/dicom_server/config.json"));
        assertFalse(command.contains("secret-123"));
        assertFalse(command.contains("b3J0aGFuYzpzZWNyZXQtMTIz"));
    }

    @Test
    void callbackBaseUrlShouldPreferDicomServerUiValue() {
        DicomServerServiceImpl service = new DicomServerServiceImpl();
        ReflectionTestUtils.setField(service, "apiAuthUrl", "http://localhost:8080/pacsApi");
        HospitalModalityServerRouteResponse route = secureRoute();
        route.setPacsApiCallbackBaseUrl("http://192.168.8.10:8080/pacsApi/");

        String resolved = ReflectionTestUtils.invokeMethod(service, "resolveCallbackApiBaseUrl", route);

        assertEquals("http://192.168.8.10:8080/pacsApi", resolved);
    }

    @Test
    void dicomServerZipPortsShouldUsePublicUiPortAndConfiguredDicomPort() {
        DicomServerServiceImpl service = new DicomServerServiceImpl();
        HospitalModalityServerRouteResponse route = secureRoute();
        route.setPort(8042);
        route.setDicomPort(4262);
        route.setDicomServerUiBaseUrl("http://localhost:8062");

        Integer httpPort = ReflectionTestUtils.invokeMethod(service, "resolveDicomServerHttpPublishPort", route);
        Integer dicomPort = ReflectionTestUtils.invokeMethod(service, "resolveDicomServerDicomPublishPort", route);

        assertEquals(8062, httpPort);
        assertEquals(4262, dicomPort);
    }

    @Test
    void dicomServerDockerfileShouldBuildFromLocalBaseWithoutFrontendSyntaxPull() {
        DicomServerServiceImpl service = new DicomServerServiceImpl();

        String dockerfile = ReflectionTestUtils.invokeMethod(service, "buildDicomServerDockerfileContent");

        assertTrue(dockerfile.startsWith("FROM dicom_server_base:latest"));
        assertFalse(dockerfile.contains("# syntax=docker/dockerfile"));
        assertFalse(dockerfile.contains("orthancteam/orthanc"));
    }

    @Test
    void dicomServerDeployScriptShouldUseOfflineBaseImageArchiveByDefault() {
        DicomServerServiceImpl service = new DicomServerServiceImpl();

        String deployScript = ReflectionTestUtils.invokeMethod(service, "buildDicomServerDeployScriptContent");

        assertTrue(deployScript.contains("images/dicom_server_base.tar"));
        assertTrue(deployScript.contains("docker load -i"));
        assertTrue(deployScript.contains("UDAYA_DICOM_SERVER_ALLOW_PULL"));
        assertTrue(deployScript.contains("docker compose build --pull=false"));
        assertTrue(deployScript.contains("docker compose up -d --force-recreate --no-build"));
        assertFalse(deployScript.contains("docker compose up -d --build --force-recreate"));
        assertTrue(deployScript.contains("docker pull \"${UPSTREAM_IMAGE}\""));
        assertTrue(deployScript.contains("if [[ \"${ALLOW_PULL,,}\" == \"true\" ]]"));
    }

    @Test
    void dicomServerCacheBaseImageScriptShouldPreferExistingLocalImage() {
        DicomServerServiceImpl service = new DicomServerServiceImpl();

        String cacheScript = ReflectionTestUtils.invokeMethod(service, "buildDicomServerCacheBaseImageScriptContent");

        assertTrue(cacheScript.contains("Archive already exists: ${IMAGE_ARCHIVE}"));
        assertTrue(cacheScript.contains("docker image inspect \"${BASE_IMAGE}\""));
        assertTrue(cacheScript.contains("Using existing local ${BASE_IMAGE}."));
        assertTrue(cacheScript.contains("docker pull \"${UPSTREAM_IMAGE}\""));
        assertTrue(cacheScript.contains("docker save -o \"${IMAGE_ARCHIVE}\" \"${BASE_IMAGE}\""));
    }

    @Test
    void dicomServerDockerComposeShouldNeverPullServiceImage() {
        DicomServerServiceImpl service = new DicomServerServiceImpl();
        DicomServerConfigBuildResponse response = new DicomServerConfigBuildResponse();
        response.setProjectName("dicom_server_ksfh");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("AuthenticationEnabled", true);
        config.put("RegisteredUsers", new LinkedHashMap<>(Map.of("dicom_server", "secret-123")));
        response.setConfig(config);

        String compose = ReflectionTestUtils.invokeMethod(service, "buildDicomServerDockerComposeContent", response);

        assertTrue(compose.contains("pull_policy: never"));
        assertTrue(compose.contains("pull: false"));
        assertTrue(compose.contains("container_name: ${UDAYA_DICOM_SERVER_CONTAINER_NAME:-dicom_server_ksfh}"));
    }

    @Test
    void dicomServerUrlsShouldDeriveFromHostPortAndDicomwebPath() {
        DicomServerServiceImpl service = new DicomServerServiceImpl();
        HospitalDicomServerRequestUpdate request = new HospitalDicomServerRequestUpdate();
        request.setIpAddress("192.168.8.12");
        request.setPort(8043);
        request.setDicomPort(4243);
        request.setSslEnabled(false);
        request.setDicomwebPath("/dicom-web");

        Object endpoint = ReflectionTestUtils.invokeMethod(service, "resolveDicomEndpoint", request, null);
        String publicUiBaseUrl = (String) ReflectionTestUtils.getField(endpoint, "baseUrl");
        String dicomwebBaseUrl = ReflectionTestUtils.invokeMethod(service, "buildDerivedDicomwebBaseUrl", publicUiBaseUrl, request.getDicomwebPath());

        assertEquals("http://192.168.8.12:8043", publicUiBaseUrl);
        assertEquals("http://192.168.8.12:8043/dicom-web", dicomwebBaseUrl);
    }

    @Test
    void dicomServerEndpointShouldPreferRequestedHostOverExistingEndpoint() {
        DicomServerServiceImpl service = new DicomServerServiceImpl();
        HospitalDicomServerResponse existing = new HospitalDicomServerResponse();
        existing.setIpAddress("192.168.8.99");
        existing.setPort(8042);
        existing.setDicomPort(4242);
        existing.setDicomwebPath("/dicom-web");

        HospitalDicomServerRequestUpdate request = new HospitalDicomServerRequestUpdate();
        request.setIpAddress("192.168.8.12");
        request.setPort(8044);
        request.setDicomPort(4244);
        request.setSslEnabled(false);

        Object endpoint = ReflectionTestUtils.invokeMethod(service, "resolveDicomEndpoint", request, existing);
        String publicUiBaseUrl = (String) ReflectionTestUtils.getField(endpoint, "baseUrl");
        String dicomwebBaseUrl = ReflectionTestUtils.invokeMethod(service, "buildDerivedDicomwebBaseUrl", publicUiBaseUrl, existing.getDicomwebPath());

        assertEquals("http://192.168.8.12:8044", publicUiBaseUrl);
        assertEquals("http://192.168.8.12:8044/dicom-web", dicomwebBaseUrl);
    }

    @Test
    void buildRoutingDicomServerConfigShouldLockHospitalIdentityAfterPackageGeneration() throws Exception {
        DicomServerServiceImpl service = new DicomServerServiceImpl();
        DicomServerMapper dicomServerMapper = mock(DicomServerMapper.class);
        OAuth2ClientMapper oauth2ClientMapper = mock(OAuth2ClientMapper.class);
        UserService userService = mock(UserService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        ReflectionTestUtils.setField(service, "dicomServerMapper", dicomServerMapper);
        ReflectionTestUtils.setField(service, "oauth2ClientMapper", oauth2ClientMapper);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "activityLogService", mock(ActivityLogService.class));
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "apiAuthUrl", "http://localhost:8080/pacsApi");

        User user = new User();
        user.setId(7L);
        when(userService.getUserAuth()).thenReturn(user);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-secret");

        HospitalDicomRoutingConfigResponse routeConfig = new HospitalDicomRoutingConfigResponse();
        routeConfig.setId(10L);
        routeConfig.setPublicKey("route-public-key");
        routeConfig.setHospitalId(1L);
        routeConfig.setHospitalPublicKey("hospital-public-key");
        routeConfig.setHospitalName("KSFH Hospital");

        HospitalModalityServerRouteResponse route = secureRoute();
        route.setRoutingConfigId(10L);
        route.setDicomServerId(33L);
        route.setDicomServerPublicKey("server-public-key");
        route.setDicomServerName("UDAYA_DICOM_SERVER KSFH");
        route.setUsername("ksfh_archive");
        route.setPassword("secret-123");
        route.setPort(8042);
        route.setDicomPort(4242);
        route.setDicomServerUiBaseUrl("http://localhost:8042");
        route.setDicomwebPath("/dicom-web");
        route.setHttpServerEnabled(true);
        route.setRemoteAccessAllowed(true);
        route.setAuthenticationEnabled(true);
        route.setAuthorizationEnabled(true);

        when(dicomServerMapper.getRoutingConfigById(10L, 1L)).thenReturn(routeConfig);
        when(dicomServerMapper.listRoutesByRoutingConfigIds(eq(List.of(10L)), eq(1L), isNull())).thenReturn(List.of(route));
        when(oauth2ClientMapper.upsertDicomServerCallbackClient(eq(33L), anyString(), anyString(), anyString())).thenReturn(1);
        when(dicomServerMapper.updateDicomServerPacsResultApiKeyHash(eq(33L), eq(1L), anyString(), eq(7L))).thenReturn(1);
        when(dicomServerMapper.markRoutingConfigPackageBuilt(10L, 1L, 7L)).thenReturn(1);

        TestingAuthenticationToken authentication = new TestingAuthenticationToken("admin", "n/a");
        authentication.setDetails(new CurrentUserPrincipal(7L, "admin", 1L, "H001", "pacs-web", "jti", 1L));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            ResponseMessage<BaseResult> response = service.buildRoutingDicomServerConfig(10L, mock(HttpServletRequest.class));

            assertTrue(response.isSuccess());
            verify(dicomServerMapper).markRoutingConfigPackageBuilt(10L, 1L, 7L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void dicomServerProjectZipShouldContainOfflineImageWorkflow() {
        DicomServerServiceImpl service = new DicomServerServiceImpl();
        DicomServerConfigBuildResponse response = new DicomServerConfigBuildResponse();
        response.setProjectName("dicom_server_ksfh");
        response.setEnvironmentContent("UDAYA_PACS_API_AUTH_CALLBACK=http://192.168.192.4:8080/pacsApi\n");
        response.setConfig(new LinkedHashMap<>(Map.of(
                "AuthenticationEnabled", true,
                "RegisteredUsers", new LinkedHashMap<>(Map.of("dicom_server", "secret-123")),
                "HttpPort", 8042
        )));
        response.setHospitalName("KSFH Hospital");
        response.setDicomServerName("UDAYA_DICOM_SERVER KSFH");
        response.setDicomServerId(4L);
        response.setCallbackClientId("dicom_server_ksfh_callback");
        response.setCallbackScriptContent("-- callback script");

        String base64 = ReflectionTestUtils.invokeMethod(service, "buildDicomServerProjectZipBase64", response);
        Map<String, String> files = unzipTextFiles(base64);

        assertTrue(files.containsKey("dicom_server_ksfh/images/README.md"));
        assertTrue(files.containsKey("dicom_server_ksfh/scripts/cache-base-image.sh"));
        assertTrue(files.containsKey("dicom_server_ksfh/scripts/deploy.sh"));
        assertTrue(files.get("dicom_server_ksfh/docker-compose.yml").contains("pull_policy: never"));
        assertTrue(files.get("dicom_server_ksfh/docker-compose.yml").contains("pull: false"));
        assertTrue(files.get("dicom_server_ksfh/scripts/deploy.sh").contains("images/dicom_server_base.tar"));
        assertTrue(files.get("dicom_server_ksfh/scripts/deploy.sh").contains("docker compose build --pull=false"));
        assertFalse(files.get("dicom_server_ksfh/scripts/deploy.sh").contains("docker compose up -d --build --force-recreate"));
        assertTrue(files.get("dicom_server_ksfh/Dockerfile").startsWith("FROM dicom_server_base:latest"));
        assertFalse(files.get("dicom_server_ksfh/Dockerfile").contains("# syntax=docker/dockerfile"));
    }

    @Test
    void dicomServerProjectZipShouldOptionallyIncludeOfflineBaseImageArchive() throws Exception {
        Path archive = Files.createTempFile("dicom-server-base", ".tar");
        Files.writeString(archive, "local-image-archive", java.nio.charset.StandardCharsets.UTF_8);
        try {
            DicomServerServiceImpl service = new DicomServerServiceImpl();
            ReflectionTestUtils.setField(service, "includeDicomServerBaseImageInPackage", true);
            ReflectionTestUtils.setField(service, "dicomServerBaseImageArchivePath", archive.toString());
            DicomServerConfigBuildResponse response = new DicomServerConfigBuildResponse();
            response.setProjectName("dicom_server_ksfh");
            response.setEnvironmentContent("UDAYA_PACS_API_AUTH_CALLBACK=http://192.168.192.4:8080/pacsApi\n");
            response.setConfig(new LinkedHashMap<>(Map.of(
                    "AuthenticationEnabled", true,
                    "RegisteredUsers", new LinkedHashMap<>(Map.of("dicom_server", "secret-123")),
                    "HttpPort", 8042
            )));
            response.setHospitalName("KSFH Hospital");
            response.setDicomServerName("UDAYA_DICOM_SERVER KSFH");
            response.setDicomServerId(4L);
            response.setCallbackClientId("dicom_server_ksfh_callback");
            response.setCallbackScriptContent("-- callback script");

            String base64 = ReflectionTestUtils.invokeMethod(service, "buildDicomServerProjectZipBase64", response);
            Map<String, byte[]> files = unzipFiles(base64);

            assertArrayEquals(
                    "local-image-archive".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    files.get("dicom_server_ksfh/images/dicom_server_base.tar")
            );
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void dicomServerProjectZipShouldSkipLargeOfflineBaseImageArchive() throws Exception {
        Path archive = Files.createTempFile("dicom-server-base-large", ".tar");
        Files.writeString(archive, "too-large-for-json-response", java.nio.charset.StandardCharsets.UTF_8);
        try {
            DicomServerServiceImpl service = new DicomServerServiceImpl();
            ReflectionTestUtils.setField(service, "includeDicomServerBaseImageInPackage", true);
            ReflectionTestUtils.setField(service, "dicomServerBaseImageArchivePath", archive.toString());
            ReflectionTestUtils.setField(service, "maxEmbeddedDicomServerBaseImageBytes", 4L);
            DicomServerConfigBuildResponse response = new DicomServerConfigBuildResponse();
            response.setProjectName("dicom_server_ksfh");
            response.setEnvironmentContent("UDAYA_PACS_API_AUTH_CALLBACK=http://192.168.192.4:8080/pacsApi\n");
            response.setConfig(new LinkedHashMap<>(Map.of(
                    "AuthenticationEnabled", true,
                    "RegisteredUsers", new LinkedHashMap<>(Map.of("dicom_server", "secret-123")),
                    "HttpPort", 8042
            )));
            response.setHospitalName("KSFH Hospital");
            response.setDicomServerName("UDAYA_DICOM_SERVER KSFH");
            response.setDicomServerId(4L);
            response.setCallbackClientId("dicom_server_ksfh_callback");
            response.setCallbackScriptContent("-- callback script");

            String base64 = ReflectionTestUtils.invokeMethod(service, "buildDicomServerProjectZipBase64", response);
            Map<String, byte[]> files = unzipFiles(base64);

            assertFalse(files.containsKey("dicom_server_ksfh/images/dicom_server_base.tar"));
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void downloadDicomServerBaseImageShouldStreamConfiguredArchive() throws Exception {
        Path archive = Files.createTempFile("dicom-server-base-download", ".tar");
        byte[] archiveBytes = "offline-docker-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(archive, archiveBytes);
        try {
            DicomServerServiceImpl service = new DicomServerServiceImpl();
            ReflectionTestUtils.setField(service, "dicomServerBaseImageArchivePath", archive.toString());
            ReflectionTestUtils.setField(service, "activityLogService", mock(ActivityLogService.class));

            ResponseEntity<Resource> response = service.downloadDicomServerBaseImage(mock(HttpServletRequest.class));

            assertEquals(200, response.getStatusCode().value());
            assertEquals((long) archiveBytes.length, response.getHeaders().getContentLength());
            assertEquals("attachment; filename=\"dicom_server_base.tar\"", response.getHeaders().getFirst("Content-Disposition"));
            assertNotNull(response.getBody());
            assertArrayEquals(archiveBytes, response.getBody().getInputStream().readAllBytes());
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void streamedDicomServerProjectZipShouldIncludeOfflineBaseImageArchive() throws Exception {
        Path archive = Files.createTempFile("dicom-server-base-stream", ".tar");
        Files.writeString(archive, "streamed-local-image", java.nio.charset.StandardCharsets.UTF_8);
        try {
            DicomServerServiceImpl service = new DicomServerServiceImpl();
            DicomServerConfigBuildResponse response = new DicomServerConfigBuildResponse();
            response.setProjectName("dicom_server_ksfh");
            response.setEnvironmentContent("UDAYA_PACS_API_AUTH_CALLBACK=http://192.168.192.4:8080/pacsApi\n");
            response.setConfig(new LinkedHashMap<>(Map.of(
                    "AuthenticationEnabled", true,
                    "RegisteredUsers", new LinkedHashMap<>(Map.of("dicom_server", "secret-123")),
                    "HttpPort", 8042
            )));
            response.setHospitalName("KSFH Hospital");
            response.setDicomServerName("UDAYA_DICOM_SERVER KSFH");
            response.setDicomServerId(4L);
            response.setCallbackClientId("dicom_server_ksfh_callback");
            response.setCallbackScriptContent("-- callback script");

            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            ReflectionTestUtils.invokeMethod(
                    service,
                    "writeDicomServerProjectZip",
                    List.of(response),
                    outputStream,
                    archive
            );
            Map<String, byte[]> files = unzipBytes(outputStream.toByteArray());

            assertArrayEquals(
                    "streamed-local-image".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    files.get("dicom_server_ksfh/images/dicom_server_base.tar")
            );
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private static Map<String, String> unzipTextFiles(String base64) {
        Map<String, byte[]> zipFiles = unzipFiles(base64);
        Map<String, String> files = new LinkedHashMap<>();
        Set<String> binarySuffixes = Set.of(".tar", ".gz", ".png", ".jpg", ".jpeg");
        zipFiles.forEach((name, bytes) -> {
            if (binarySuffixes.stream().noneMatch(name::endsWith)) {
                files.put(name, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            }
        });
        return files;
    }

    private static Map<String, byte[]> unzipFiles(String base64) {
        return unzipBytes(java.util.Base64.getDecoder().decode(base64));
    }

    private static Map<String, byte[]> unzipBytes(byte[] zipBytes) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                files.put(name, zip.readAllBytes());
            }
            return files;
        } catch (java.io.IOException error) {
            throw new AssertionError("Unable to inspect generated zip", error);
        }
    }

    private static HospitalModalityServerRouteResponse secureRoute() {
        HospitalModalityServerRouteResponse route = new HospitalModalityServerRouteResponse();
        route.setDicomServerId(1L);
        route.setDicomServerName("DicomServer KSFH");
        route.setHospitalId(1L);
        route.setAeTitle("dicom_server");
        route.setUsername("dicom_server");
        route.setPassword("secret-123");
        route.setMachineAeTitle("KSFH_CT01");
        route.setMachineHost("192.168.40.21");
        route.setMachinePort(104);
        route.setModalityAbbr("CT");
        route.setWorklistsEnabled(true);
        return route;
    }
}
