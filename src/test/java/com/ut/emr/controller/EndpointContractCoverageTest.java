package com.ut.emrPacs.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointContractCoverageTest {

    private static final Pattern PATH_VAR_REGEX = Pattern.compile("\\{([^}:]+):[^}]+}");

    @Test
    void listedEndpointsShouldExistWithExpectedHttpMethod() {
        Set<String> actual = collectEndpoints(
                AuthController.class,
                DropDownController.class,
                HospitalController.class,
                ModalityController.class,
                HospitalModalityController.class,
                ModuleTypeController.class,
                PermissionController.class,
                PatientController.class,
                RoleController.class,
                WorklistController.class,
                SystemActivityController.class,
                StudyController.class,
                FileUploadController.class,
                UserController.class,
                UserLogController.class,
                UserProfileController.class,
                DicomServerController.class,
                DicomMachineController.class,
                DicomRoutingController.class,
                PacsResultApiController.class
        );

        List<String> expected = List.of(
                "POST /auth/auth-refresh",
                "POST /auth/auth-logout",
                "POST /auth/auth-login",
                "POST /auth/auth-client-credentials",
                "POST /dropdown/dropdown-nationality",
                "POST /hospital/hospital-update",
                "POST /hospital/hospital-list",
                "POST /hospital/hospital-find/{id}",
                "POST /hospital/hospital-create",
                "POST /modality/modality-update",
                "POST /modality/modality-list",
                "POST /modality/modality-find/{id}",
                "POST /modality/modality-delete/{id}",
                "POST /modality/modality-create",
                "POST /hospital-modality",
                "POST /module-type/find/{id}",
                "POST /module-type/module-type-list",
                "POST /permission/permission-tree",
                "POST /permission/permission-save-role-permissions",
                "POST /patient/patient-update",
                "POST /patient/patient-list",
                "POST /patient/patient-find/{id}",
                "POST /patient/patient-create",
                "POST /role/role-update",
                "POST /role/role-menu",
                "POST /role/role-list",
                "POST /role/user-group-list",
                "POST /role/role-find/{id}",
                "POST /role/role-delete/{id}",
                "POST /role/role-add",
                "POST /worklist/worklist-view-study",
                "POST /worklist/worklist-send-to-pacs",
                "POST /worklist/worklist-return",
                "POST /worklist/worklist-received-study",
                "POST /worklist/worklist-list",
                "POST /worklist/worklist-cancel",
                "POST /worklist/worklist-assign",
                "POST /system-activity/system-activity-list",
                "POST /system-activity/system-activity-find/{id}",
                "POST /study/study-list",
                "POST /study/study-find/{id}",
                "POST /file/file-upload",
                "GET /file/file-upload/{filename}",
                "DELETE /file/file-delete",
                "POST /user/user-update",
                "POST /user/user-me",
                "POST /user/user-list",
                "POST /user/user-find/{id}",
                "POST /user/user-delete/{id}",
                "POST /user/user-create",
                "POST /user/user-change-password",
                "POST /report/user-log/user-log-list",
                "POST /report/user-log/user-log-find/{id}",
                "POST /user-profile/user-profile-update",
                "POST /user-profile/user-profile-get",
                "POST /dicom-server/dicom-server-list",
                "POST /dicom-server/dicom-server-find/{id}",
                "POST /dicom-server/dicom-server-create",
                "POST /dicom-server/dicom-server-update",
                "POST /dicom-server/dicom-server-delete/{id}",
                "POST /dicom-machine/dicom-machine-list",
                "POST /dicom-machine/dicom-machine-find/{id}",
                "POST /dicom-machine/dicom-machine-create",
                "POST /dicom-machine/dicom-machine-update",
                "POST /dicom-machine/dicom-machine-delete/{id}",
                "POST /dicom-routing/dicom-routing-list",
                "POST /dicom-routing/dicom-routing-find/{id}",
                "POST /dicom-routing/dicom-routing-create",
                "POST /dicom-routing/dicom-routing-update",
                "POST /dicom-routing/dicom-routing-delete/{id}",
                "POST /pacs-result-api/pacs-result-viewer-state-find",
                "POST /pacs-result-api/pacs-result-viewer-state-save",
                "POST /pacs-result-api/pacs-result-viewer-state-delete"
        );

        List<String> missing = expected.stream().filter(endpoint -> !actual.contains(endpoint)).toList();
        assertTrue(missing.isEmpty(), () -> "Missing endpoint mappings: " + missing);

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

    private Set<String> collectEndpoints(Class<?>... controllerClasses) {
        Set<String> endpoints = new LinkedHashSet<>();
        for (Class<?> controllerClass : controllerClasses) {
            String[] classPaths = resolveClassPaths(controllerClass);
            for (Method method : controllerClass.getDeclaredMethods()) {
                collectMethodEndpoints(endpoints, classPaths, method);
            }
        }
        return endpoints;
    }

    private void collectMethodEndpoints(Set<String> endpoints, String[] classPaths, Method method) {
        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        if (postMapping != null) {
            for (String path : extractPaths(postMapping.value(), postMapping.path())) {
                addEndpoint(endpoints, "POST", classPaths, path);
            }
            return;
        }

        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        if (getMapping != null) {
            for (String path : extractPaths(getMapping.value(), getMapping.path())) {
                addEndpoint(endpoints, "GET", classPaths, path);
            }
            return;
        }

        DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
        if (deleteMapping != null) {
            for (String path : extractPaths(deleteMapping.value(), deleteMapping.path())) {
                addEndpoint(endpoints, "DELETE", classPaths, path);
            }
            return;
        }

        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        if (requestMapping == null) {
            return;
        }
        String[] paths = extractPaths(requestMapping.value(), requestMapping.path());
        RequestMethod[] methods = requestMapping.method();
        if (methods.length == 0) {
            for (String path : paths) {
                addEndpoint(endpoints, "ANY", classPaths, path);
            }
            return;
        }
        for (RequestMethod requestMethod : methods) {
            for (String path : paths) {
                addEndpoint(endpoints, requestMethod.name(), classPaths, path);
            }
        }
    }

    private String[] resolveClassPaths(Class<?> controllerClass) {
        RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
        if (requestMapping == null) {
            return new String[]{""};
        }
        return extractPaths(requestMapping.value(), requestMapping.path());
    }

    private String[] extractPaths(String[] values, String[] paths) {
        List<String> merged = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    merged.add(value.trim());
                }
            }
        }
        if (paths != null) {
            for (String path : paths) {
                if (path != null && !path.isBlank()) {
                    merged.add(path.trim());
                }
            }
        }
        if (merged.isEmpty()) {
            return new String[]{""};
        }
        return merged.toArray(String[]::new);
    }

    private void addEndpoint(Set<String> endpoints, String method, String[] classPaths, String methodPath) {
        for (String classPath : classPaths) {
            String normalized = normalizePath(classPath + methodPath);
            endpoints.add(method + " " + normalized);
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.replaceAll("/+", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        normalized = PATH_VAR_REGEX.matcher(normalized).replaceAll("{$1}");
        return normalized;
    }
}
