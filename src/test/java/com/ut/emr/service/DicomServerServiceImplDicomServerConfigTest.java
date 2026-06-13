package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomRoutingConfigResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalModalityServerRouteResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.DicomServerConfigBuildResponse;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalDicomServerRequestUpdate;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
