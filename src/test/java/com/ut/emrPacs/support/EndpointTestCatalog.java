package com.ut.emrPacs.support;

import com.ut.emrPacs.controller.AuthController;
import com.ut.emrPacs.controller.ApplicationSettingsController;
import com.ut.emrPacs.controller.DashboardController;
import com.ut.emrPacs.controller.DicomChunkUploadController;
import com.ut.emrPacs.controller.DicomMachineController;
import com.ut.emrPacs.controller.DicomRoutingController;
import com.ut.emrPacs.controller.DicomServerController;
import com.ut.emrPacs.controller.DicomUploadController;
import com.ut.emrPacs.controller.DropDownController;
import com.ut.emrPacs.controller.FileUploadController;
import com.ut.emrPacs.controller.HospitalController;
import com.ut.emrPacs.controller.HospitalModalityController;
import com.ut.emrPacs.controller.ModalityController;
import com.ut.emrPacs.controller.ModuleTypeController;
import com.ut.emrPacs.controller.NotificationController;
import com.ut.emrPacs.controller.PacsResultApiController;
import com.ut.emrPacs.controller.PacsResultController;
import com.ut.emrPacs.controller.PacsResultTemplateController;
import com.ut.emrPacs.controller.PatientController;
import com.ut.emrPacs.controller.PermissionController;
import com.ut.emrPacs.controller.RoleController;
import com.ut.emrPacs.controller.SecurityReportController;
import com.ut.emrPacs.controller.StudyController;
import com.ut.emrPacs.controller.StudyRetentionController;
import com.ut.emrPacs.controller.SystemActivityController;
import com.ut.emrPacs.controller.UserController;
import com.ut.emrPacs.controller.UserLogController;
import com.ut.emrPacs.controller.UserProfileController;
import com.ut.emrPacs.controller.WorklistController;
import com.ut.emrPacs.controller.WorklistRestController;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EndpointTestCatalog {

    private static final Pattern PATH_VAR_REGEX = Pattern.compile("\\{([^}:]+):[^}]+}");
    private static final Pattern PATH_VAR_ANY_REGEX = Pattern.compile("\\{([^}]+)}");

    public static final List<Class<?>> CONTROLLER_CLASSES = List.of(
            ApplicationSettingsController.class,
            AuthController.class,
            DashboardController.class,
            DicomChunkUploadController.class,
            DicomMachineController.class,
            DicomRoutingController.class,
            DicomServerController.class,
            DicomUploadController.class,
            DropDownController.class,
            FileUploadController.class,
            HospitalController.class,
            HospitalModalityController.class,
            ModalityController.class,
            ModuleTypeController.class,
            NotificationController.class,
            PacsResultApiController.class,
            PacsResultController.class,
            PacsResultTemplateController.class,
            PatientController.class,
            PermissionController.class,
            RoleController.class,
            SecurityReportController.class,
            StudyController.class,
            StudyRetentionController.class,
            SystemActivityController.class,
            UserController.class,
            UserLogController.class,
            UserProfileController.class,
            WorklistController.class,
            WorklistRestController.class
    );

    private EndpointTestCatalog() {
    }

    public static List<EndpointSpec> allEndpoints() {
        List<EndpointSpec> endpoints = new ArrayList<>();
        for (Class<?> controllerClass : CONTROLLER_CLASSES) {
            String[] classPaths = resolveClassPaths(controllerClass);
            for (Method method : controllerClass.getDeclaredMethods()) {
                collectMethodEndpoints(endpoints, controllerClass, classPaths, method);
            }
        }
        return endpoints.stream()
                .sorted(Comparator.comparing(EndpointSpec::displayName))
                .toList();
    }

    public static Set<String> scannedRestControllers() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> classNames = new LinkedHashSet<>();
        scanner.findCandidateComponents("com.ut.emrPacs.controller").forEach(component -> classNames.add(component.getBeanClassName()));
        return classNames;
    }

    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.replaceAll("/+", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return PATH_VAR_REGEX.matcher(normalized).replaceAll("{$1}");
    }

    private static void collectMethodEndpoints(List<EndpointSpec> endpoints, Class<?> controllerClass, String[] classPaths, Method method) {
        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        if (postMapping != null) {
            addEndpoints(endpoints, controllerClass, method, "POST", classPaths, extractPaths(postMapping.value(), postMapping.path()), postMapping.consumes());
            return;
        }

        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        if (getMapping != null) {
            addEndpoints(endpoints, controllerClass, method, "GET", classPaths, extractPaths(getMapping.value(), getMapping.path()), getMapping.consumes());
            return;
        }

        PutMapping putMapping = method.getAnnotation(PutMapping.class);
        if (putMapping != null) {
            addEndpoints(endpoints, controllerClass, method, "PUT", classPaths, extractPaths(putMapping.value(), putMapping.path()), putMapping.consumes());
            return;
        }

        PatchMapping patchMapping = method.getAnnotation(PatchMapping.class);
        if (patchMapping != null) {
            addEndpoints(endpoints, controllerClass, method, "PATCH", classPaths, extractPaths(patchMapping.value(), patchMapping.path()), patchMapping.consumes());
            return;
        }

        DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
        if (deleteMapping != null) {
            addEndpoints(endpoints, controllerClass, method, "DELETE", classPaths, extractPaths(deleteMapping.value(), deleteMapping.path()), deleteMapping.consumes());
            return;
        }

        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        if (requestMapping == null) {
            return;
        }
        String[] paths = extractPaths(requestMapping.value(), requestMapping.path());
        RequestMethod[] methods = requestMapping.method();
        if (methods.length == 0) {
            addEndpoints(endpoints, controllerClass, method, "ANY", classPaths, paths, requestMapping.consumes());
            return;
        }
        for (RequestMethod requestMethod : methods) {
            addEndpoints(endpoints, controllerClass, method, requestMethod.name(), classPaths, paths, requestMapping.consumes());
        }
    }

    private static String[] resolveClassPaths(Class<?> controllerClass) {
        RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
        if (requestMapping == null) {
            return new String[]{""};
        }
        return extractPaths(requestMapping.value(), requestMapping.path());
    }

    private static String[] extractPaths(String[] values, String[] paths) {
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

    private static void addEndpoints(
            List<EndpointSpec> endpoints,
            Class<?> controllerClass,
            Method javaMethod,
            String httpMethod,
            String[] classPaths,
            String[] methodPaths,
            String[] consumes
    ) {
        for (String classPath : classPaths) {
            for (String methodPath : methodPaths) {
                String path = normalizePath(classPath + methodPath);
                endpoints.add(new EndpointSpec(
                        httpMethod,
                        path,
                        consumes == null ? List.of() : List.of(consumes),
                        controllerClass.getSimpleName(),
                        javaMethod.getName()
                ));
            }
        }
    }

    public record EndpointSpec(String method, String path, List<String> consumes, String controller, String javaMethod) {
        public String displayName() {
            return method + " " + path;
        }

        public boolean isMultipart() {
            return consumes.stream().anyMatch(value -> value != null && value.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE));
        }

        public boolean sendsJsonBody() {
            return !isMultipart()
                    && !method.equals("GET")
                    && !method.equals("HEAD")
                    && !method.equals("OPTIONS");
        }

        public String concretePath() {
            String value = path.replace("/**", "/tail");
            Matcher matcher = PATH_VAR_ANY_REGEX.matcher(value);
            StringBuffer resolved = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(resolved, samplePathValue(matcher.group(1)));
            }
            matcher.appendTail(resolved);
            return normalizePath(resolved.toString());
        }

        private static String samplePathValue(String variableName) {
            String normalized = variableName == null ? "" : variableName.toLowerCase();
            if (normalized.contains("filename")) {
                return "sample.png";
            }
            if (normalized.contains("token")) {
                return "viewer-token";
            }
            if (normalized.contains("template")) {
                return "template-key";
            }
            if (normalized.contains("instance")) {
                return "instance-1";
            }
            if (normalized.contains("id") || normalized.contains("key")) {
                return "1";
            }
            return "sample";
        }
    }
}
