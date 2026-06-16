package com.ut.emrPacs.controller;

import com.ut.emrPacs.support.EndpointTestCatalog;
import com.ut.emrPacs.support.EndpointTestCatalog.EndpointSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointContractCoverageTest {

    @Test
    void catalogShouldIncludeEveryRestControllerInControllerPackage() {
        Set<String> expected = EndpointTestCatalog.CONTROLLER_CLASSES.stream()
                .map(Class::getName)
                .collect(Collectors.toSet());

        assertEquals(expected, EndpointTestCatalog.scannedRestControllers());
    }

    @Test
    void controllerEndpointInventoryShouldCoverAllBusinessAreas() {
        Set<String> actual = EndpointTestCatalog.allEndpoints().stream()
                .map(EndpointSpec::displayName)
                .collect(Collectors.toSet());

        assertTrue(EndpointTestCatalog.CONTROLLER_CLASSES.size() >= 25, "Controller catalog unexpectedly shrank");
        assertTrue(actual.size() >= 100, () -> "Endpoint catalog unexpectedly small: " + actual.size());

        List<String> required = List.of(
                "POST /auth/auth-login",
                "POST /auth/auth-client-credentials",
                "POST /dashboard/dashboard-overview",
                "POST /dicom-uploads",
                "POST /dicom-server/dicom-server-health-list",
                "POST /dicom-server/dicom-server-health-summary",
                "POST /dicom-server/dicom-server-health-settings-get",
                "POST /dicom-server/dicom-server-health-settings-update",
                "POST /dicom-routing/dicom-routing-build-config/{id}",
                "POST /dropdown/dropdown-dicom-server",
                "POST /notification/notification-list",
                "GET /notification/notification-stream",
                "POST /pacs-result/pacs-result-create",
                "POST /pacs-result/pacs-result-image-upload",
                "POST /pacs-result/pacs-result-template-find/{templateKey}",
                "POST /pacs-result-api/public-viewer-authorize",
                "POST /pacs-result-template/pacs-result-template-list",
                "POST /study/study-list",
                "GET /study/{studyId}/viewer-info",
                "POST /worklist/worklist-find",
                "POST /worklist/worklist-received-study",
                "GET /worklist/{worklistId}/viewer-info",
                "GET /worklist/viewer-dicom-web/{viewerToken}/{hospitalId}/{worklistId}/**",
                "HEAD /worklist/viewer-dicom-web/{viewerToken}/{hospitalId}/{worklistId}/**",
                "POST /worklist/viewer-dicom-web-authorize",
                "POST /worklist/viewer-dicom-web-renew",
                "POST /user/user-group-list"
        );

        List<String> missing = required.stream().filter(endpoint -> !actual.contains(endpoint)).toList();
        assertTrue(missing.isEmpty(), () -> "Missing required endpoint mappings: " + missing);
    }

    @Test
    void removedLegacyAliasesShouldStayRemoved() {
        Set<String> actual = EndpointTestCatalog.allEndpoints().stream()
                .map(EndpointSpec::displayName)
                .collect(Collectors.toSet());

        List<String> removedAliases = List.of(
                "POST /user/list",
                "POST /user/add",
                "POST /user/change-password",
                "POST /user/me",
                "POST /user/find/{id}",
                "POST /user/update",
                "POST /user/delete/{id}",
                "POST /role/list",
                "POST /role/create",
                "POST /role/menu",
                "POST /role/find/{id}",
                "POST /role/update",
                "POST /role/delete/{id}",
                "POST /role/add"
        );
        List<String> stillPresent = removedAliases.stream().filter(actual::contains).toList();
        assertTrue(stillPresent.isEmpty(), () -> "Duplicate alias endpoints still present: " + stillPresent);
    }
}
