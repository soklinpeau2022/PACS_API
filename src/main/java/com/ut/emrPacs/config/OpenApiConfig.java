package com.ut.emrPacs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Modern Swagger/OpenAPI configuration for EMR backend.
 * - Adds Bearer token authentication for all endpoints.
 * - Dynamically groups APIs by controller @Tag annotation.
 * - Provides a global "ALL APIs" group at the top.
 * - Supports dynamic server URLs via springdoc.server-url.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";
    private static final Pattern LEADING_ORDER_PATTERN = Pattern.compile("^\\s*(\\d+)\\s*[.)-]?\\s*(.*)$");
    private static final Map<String, String> REQUEST_EXAMPLES = buildRequestExamples();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private final RequestMappingHandlerMapping handlerMapping;

    @Value("${springdoc.server-url:}")
    private String serverUrl;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public OpenApiConfig(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    /**
     * OpenAPI bean: sets title, description, version, security, and optional server URL.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("UDAYA_PACS_API")
                        .version("1.0.0")
                        .description("API documentation for the UDAYA_PACS_API System."))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(
                        SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("Opaque")
                ));

        openAPI.setServers(buildServers(serverUrl, contextPath));

        return openAPI;
    }

    @Bean
    public OpenApiCustomizer requestPayloadExampleCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((path, pathItem) -> {
                String example = REQUEST_EXAMPLES.get(path);
                if (example == null || pathItem == null) {
                    return;
                }

                appendExample(pathItem.getPost(), example);
                appendExample(pathItem.getPut(), example);
                appendExample(pathItem.getPatch(), example);
                appendExample(pathItem.getDelete(), example);
            });
        };
    }

    /**
     * Groups API endpoints for Swagger UI:
     * - Named by controller @Tag(name).
     * - Ordered by leading integer if present, else alphabetically.
     * - "0.ALL APIs" at top for global search.
     */
    @Bean
    public List<GroupedOpenApi> apiGroups() {
        Map<String, Set<String>> tagToPaths = extractTagToPaths();

        List<Map.Entry<String, Set<String>>> sortedEntries = tagToPaths.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<String, ?> entry) -> parseLeadingInt(entry.getKey()))
                        .thenComparing(Map.Entry::getKey, String::compareToIgnoreCase))
                .toList();

        Map<String, String> groupIds = buildUniqueGroupIds(sortedEntries.stream()
                .map(Map.Entry::getKey)
                .toList());

        List<GroupedOpenApi> groupApis = sortedEntries.stream()
                .map(entry -> {
                    String tagName = entry.getKey().trim();
                    String groupId = groupIds.getOrDefault(entry.getKey(), toSafeGroupId(tagName));
                    return GroupedOpenApi.builder()
                            .group(groupId)
                            .addOpenApiCustomizer(openApi -> openApi.info(new Info().description("API endpoints for " + tagName)))
                            .pathsToMatch(entry.getValue().toArray(String[]::new))
                            .build();
                })
                .toList();

        GroupedOpenApi allApisGroup = GroupedOpenApi.builder()
                .group("0.ALL_APIs")
                .addOpenApiCustomizer(openApi -> {
                    if (openApi.getTags() != null) {
                        openApi.getTags().sort((a, b) -> {
                            int cmp = Integer.compare(parseLeadingInt(a.getName()), parseLeadingInt(b.getName()));
                            if (cmp != 0) return cmp;
                            return a.getName().compareToIgnoreCase(b.getName());
                        });
                    }
                })
                .build();

        List<GroupedOpenApi> result = new ArrayList<>();
        result.add(allApisGroup);
        result.addAll(groupApis);
        return result;
    }

    private static Map<String, String> buildUniqueGroupIds(List<String> rawNames) {
        Map<String, String> out = new HashMap<>();
        Set<String> used = new HashSet<>();

        for (String rawName : rawNames) {
            int order = parseLeadingInt(rawName);
            String baseName = toSafeGroupId(rawName);
            String base = order == Integer.MAX_VALUE
                    ? baseName
                    : String.format("%03d_%s", order, baseName);
            String candidate = base;
            int index = 2;
            while (used.contains(candidate.toLowerCase(Locale.ROOT))) {
                candidate = base + "_" + index;
                index++;
            }
            used.add(candidate.toLowerCase(Locale.ROOT));
            out.put(rawName, candidate);
        }

        return out;
    }

    private static String toSafeGroupId(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "group";
        }
        String normalized = rawName.trim();
        normalized = normalized.replaceAll("\\s+", "_");
        normalized = normalized.replaceAll("[^A-Za-z0-9._-]", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+", "");
        normalized = normalized.replaceAll("_+$", "");
        return normalized.isBlank() ? "group" : normalized;
    }

    /**
     * Maps @Tag.name() to a set of endpoint paths for each controller.
     */
    private Map<String, Set<String>> extractTagToPaths() {
        Map<String, Set<String>> tagToPaths = new HashMap<>();
        if (handlerMapping == null) {
            return tagToPaths;
        }

        handlerMapping.getHandlerMethods().forEach((mappingInfo, handlerMethod) -> {
            Tag tag = handlerMethod.getBeanType().getAnnotation(Tag.class);
            if (tag == null) return;

            Set<String> paths = tagToPaths.computeIfAbsent(tag.name(), k -> new HashSet<>());

            Set<String> pathPatterns = mappingInfo.getPatternValues();
            if (!pathPatterns.isEmpty()) {
                paths.addAll(pathPatterns);
            }
        });

        return tagToPaths;
    }

    /**
     * Parses the leading integer from a string (for tag/group sorting).
     * Returns Integer.MAX_VALUE if no leading integer found.
     */
    private static int parseLeadingInt(String text) {
        if (text == null || text.isBlank()) {
            return Integer.MAX_VALUE;
        }

        Matcher matcher = LEADING_ORDER_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return Integer.MAX_VALUE;
        }

        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private static String normalizeContextPath(String value) {
        if (value == null || value.isBlank() || "/".equals(value.trim())) {
            return "/";
        }
        String normalized = value.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static List<Server> buildServers(String configuredServerUrl, String configuredContextPath) {
        if (configuredServerUrl != null && !configuredServerUrl.isBlank()) {
            return Pattern.compile(",")
                    .splitAsStream(configuredServerUrl)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(OpenApiConfig::normalizeServerUrl)
                    .distinct()
                    .map(value -> new Server().url(value))
                    .toList();
        }

        // Relative URL keeps Swagger usable from localhost, LAN IP, or reverse proxy.
        return List.of(new Server().url(normalizeContextPath(configuredContextPath)));
    }

    private static String normalizeServerUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static void appendExample(io.swagger.v3.oas.models.Operation operation, String example) {
        if (operation == null || example == null || example.isBlank()) {
            return;
        }
        String original = operation.getDescription() == null ? "" : operation.getDescription().trim();
        String appendix = "Frontend example payload:\n```json\n" + example + "\n```";
        operation.setDescription(original.isBlank() ? appendix : (original + "\n\n" + appendix));
        applyRequestBodyExample(operation, example);
    }

    private static void applyRequestBodyExample(io.swagger.v3.oas.models.Operation operation, String example) {
        if (operation == null || operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
            return;
        }
        var jsonContent = operation.getRequestBody().getContent().get("application/json");
        if (jsonContent == null) {
            return;
        }
        try {
            jsonContent.setExample(JSON_MAPPER.readValue(example, Object.class));
        } catch (Exception ignored) {
            jsonContent.setExample(example);
        }
    }

    private static Map<String, String> buildRequestExamples() {
        Map<String, String> examples = new HashMap<>();

        examples.put("/auth/auth-login", "{\"clientId\":\"pacs-web\",\"username\":\"replace_with_username\",\"password\":\"replace_with_password\"}");
        examples.put("/auth/auth-logout", "{\"clientId\":\"pacs-web\"}");
        examples.put("/auth/auth-refresh", "{\"clientId\":\"pacs-web\"}");
        examples.put("/auth/auth-client-credentials", "{\"clientId\":\"pacs-web\",\"clientSecret\":\"replace_with_client_secret\"}");

        examples.put("/dropdown/dropdown-nationality", "{\"searchText\":\"th\",\"rowsPerPage\":20,\"page\":1}");
        examples.put("/dropdown/dropdown-hospital", "{\"searchText\":\"central\",\"rowsPerPage\":20,\"page\":1}");
        examples.put("/dropdown/dropdown-modality", "{\"searchText\":\"ct\",\"rowsPerPage\":20,\"page\":1}");

        examples.put("/hospital/hospital-list", "{\"searchText\":\"general\",\"rowsPerPage\":20,\"page\":1}");
        examples.put("/hospital/hospital-find/{id}", "{}");
        examples.put("/hospital/hospital-update", "{\"id\":1,\"name\":\"Main Hospital\",\"abbr\":\"MAIN\",\"code\":\"HSP001\",\"timezone\":\"Asia/Bangkok\",\"hospitalUserList\":[1,2,3],\"modalityIds\":[1,2]}");
        examples.put("/hospital/hospital-create", "{\"name\":\"New Hospital\",\"abbr\":\"NEW\",\"code\":\"HSP002\",\"timezone\":\"Asia/Bangkok\",\"hospitalUserList\":[1,2],\"modalityIds\":[1,2]}");

        examples.put("/modality/modality-list", "{\"searchText\":\"ct\",\"rowsPerPage\":20,\"page\":1}");
        examples.put("/modality/modality-find/{id}", "{}");
        examples.put("/modality/modality-create", "{\"name\":\"CT SCAN\",\"abbr\":\"CT\",\"isActive\":1}");
        examples.put("/modality/modality-update", "{\"id\":3,\"name\":\"CT SCAN\",\"abbr\":\"CT\",\"isActive\":1}");
        examples.put("/modality/modality-delete/{id}", "{}");

        examples.put("/module-type/find/{id}", "{}");
        examples.put("/module-type/module-type-list", "{\"searchText\":\"system\",\"rowsPerPage\":20,\"page\":1}");

        examples.put("/permission/permission-tree", "{\"roleId\":1}");
        examples.put("/permission/permission-save-role-permissions", "{\"roleId\":1,\"moduleDetailIds\":[101,102,103]}");

        examples.put("/patient/patient-list", "{\"searchText\":\"john\",\"rowsPerPage\":20,\"page\":1}");
        examples.put("/patient/patient-find/{id}", "{}");
        examples.put("/patient/patient-create", "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"gender\":\"M\",\"dateOfBirth\":\"1990-01-01\",\"phoneNumber\":\"0812345678\",\"address\":\"Bangkok\"}");
        examples.put("/patient/patient-update", "{\"id\":1,\"firstName\":\"John\",\"lastName\":\"Doe\",\"gender\":\"M\",\"dateOfBirth\":\"1990-01-01\",\"phoneNumber\":\"0812345678\",\"address\":\"Bangkok\"}");

        examples.put("/role/role-list", "{\"searchText\":\"admin\",\"rowsPerPage\":20,\"page\":1}");
        examples.put("/role/user-groupl-list", "{\"searchText\":\"admin\",\"rowsPerPage\":20,\"page\":1}");
        examples.put("/role/role-find/{id}", "{}");
        examples.put("/role/role-add", "{\"name\":\"Radiologist\",\"userIds\":[1,2],\"moduleDetailIds\":[101,102]}");
        examples.put("/role/role-update", "{\"id\":2,\"name\":\"Radiologist\",\"userIds\":[1,3],\"moduleDetailIds\":[101,103]}");
        examples.put("/role/role-menu", "{}");
        examples.put("/role/role-delete/{id}", "{}");

        examples.put("/worklist/worklist-list", "{\"searchText\":\"john\",\"modalityId\":3,\"statuses\":[\"WAITING\",\"IN_PROGRESS\",\"CANCELLED\",\"FAILED\"],\"dateFrom\":\"2026-05-01\",\"dateTo\":\"2026-05-16\",\"rowsPerPage\":20,\"page\":1}");
        examples.put("/worklist/worklist-assign", "{\"patientId\":1,\"modalityId\":3,\"notes\":\"Walk-in patient\"}");
        examples.put("/worklist/worklist-find", "{\"id\":1201}");
        examples.put("/worklist/worklist-update", "{\"id\":1201,\"modalityId\":3,\"studyDescription\":\"CT Chest\",\"scheduledDate\":\"2026-05-22\",\"scheduledTime\":\"09:00\",\"notes\":\"Worklist form update\"}");
        examples.put("/worklist/worklist-routed-modality-list", "{\"hospitalId\":1}");
        examples.put("/worklist/worklist-route-availability", "{\"hospitalId\":1,\"modalityId\":2}");
        examples.put("/worklist/worklist-send-to-pacs", "{\"worklistId\":1201}");
        examples.put("/worklist/worklist-sync-result", "{\"id\":1201,\"notes\":\"Manual backup sync by accession number\"}");
        examples.put("/worklist/worklist-received-study", "{\"event\":\"STUDY_RECEIVED\",\"status\":\"IN_PROGRESS\",\"accessionNumber\":\"VIEW-SM-0005\",\"dicomServerStudyId\":\"c958bd3d-5ba52826-744a96cb-686e0691-65ac3044\",\"dicomServerPatientId\":\"1828c3f0-fb9402db-239b7259-9c86c4dd-acd62796\",\"dicomServerSeriesIds\":[\"40d1ffe1-e43ebeda-5f7d0282-02589bd1-afeff9b4\"],\"studyInstanceUid\":\"1.2.826.0.1.3680043.8.498.92126578454577529120584583594987590614\",\"patientId\":\"SEED-VIEW-SM-001\",\"patientName\":\"Viewer Microscopy\",\"patientBirthDate\":\"19790417\",\"patientSex\":\"F\",\"studyDescription\":\"Viewer Microscopy\",\"studyDate\":\"20260524\",\"studyTime\":\"130000\"}");
        examples.put("/worklist/worklist-view-study", "{\"worklistId\":1201}");
        examples.put("/worklist/{worklistId}/viewer-info", "{}");
        examples.put("/worklist/worklist-return", "{\"id\":1201,\"notes\":\"Need additional image\"}");
        examples.put("/worklist/worklist-cancel", "{\"id\":1201,\"notes\":\"Patient cancelled\"}");

        examples.put("/study/study-list", "{\"searchText\":\"john\",\"status\":\"IMAGE_RECEIVED\",\"rowsPerPage\":20,\"page\":1}");
        examples.put("/study/study-find/{id}", "{}");

        examples.put("/system-activity/system-activity-list", "{\"searchText\":\"Worklist\",\"status\":1,\"rowsPerPage\":20,\"page\":1}");
        examples.put("/system-activity/system-activity-find/{id}", "{}");

        examples.put("/report/user-log/user-log-list", "{\"searchText\":\"admin\",\"rowsPerPage\":20,\"page\":1}");
        examples.put("/report/user-log/user-log-find/{id}", "{}");
        examples.put("/notification/notification-list", "{\"sources\":[\"WORKLIST\",\"STUDY\"],\"days\":14,\"rowsPerPage\":8,\"page\":1}");

        examples.put("/user/user-list", "{\"searchText\":\"john\",\"hospitalId\":1,\"rowsPerPage\":20,\"page\":1}");
        examples.put("/user/user-group-list", "{\"searchText\":\"account\",\"hospitalId\":1,\"rowsPerPage\":20,\"page\":1}");
        examples.put("/user/user-create", "{\"username\":\"john.doe\",\"email\":\"john@hospital.com\",\"password\":\"P@ssw0rd\",\"first_name\":\"John\",\"last_name\":\"Doe\",\"hospital_id\":1}");
        examples.put("/user/user-change-password", "{\"oldPassword\":\"Old@123\",\"newPassword\":\"New@123\",\"confirmPassword\":\"New@123\"}");
        examples.put("/user/user-me", "{}");
        examples.put("/user/user-find/{id}", "{}");
        examples.put("/user/user-update", "{\"id\":10,\"first_name\":\"John\",\"last_name\":\"Doe\",\"email\":\"john@hospital.com\",\"telephone\":\"0812345678\",\"hospital_id\":1}");
        examples.put("/user/user-delete/{id}", "{}");

        examples.put("/dropdown/dropdown-user-group-member", "{\"hospitalId\":1,\"searchText\":\"john\",\"rowsPerPage\":20,\"page\":1}");

        examples.put("/user-profile/user-profile-get", "{}");
        examples.put("/user-profile/user-profile-update", "{\"name\":\"John Doe\",\"email\":\"john@hospital.com\",\"phoneNumber\":\"0812345678\",\"oldPassword\":\"Old@123\",\"newPassword\":\"New@123\"}");

        examples.put("/hospital-modality", "{\"searchText\":\"ct\",\"rowsPerPage\":20,\"page\":1}");
        examples.put("/dicom-machine/dicom-machine-list", "{\"hospitalId\":1,\"modalityId\":3,\"searchText\":\"room\",\"rowsPerPage\":20,\"page\":1}");
        examples.put("/dicom-machine/dicom-machine-find/{id}", "{}");
        examples.put("/dicom-machine/dicom-machine-create", "{\"hospitalId\":1,\"modalityId\":3,\"machineName\":\"CT Scanner Room 1\",\"machineAeTitle\":\"CTROOM1\",\"machineHost\":\"10.10.10.51\",\"machinePort\":104}");
        examples.put("/dicom-machine/dicom-machine-update", "{\"id\":1,\"hospitalId\":1,\"modalityId\":3,\"machineName\":\"CT Scanner Room 1\",\"machineAeTitle\":\"CTROOM1\",\"machineHost\":\"10.10.10.51\",\"machinePort\":104}");
        examples.put("/dicom-machine/dicom-machine-delete/{id}", "{}");
        examples.put("/dicom-routing/dicom-routing-create", "{\"hospitalId\":1,\"dicomServerId\":1,\"routes\":[{\"machineId\":1,\"modalityId\":3}]}");
        examples.put("/dicom-routing/dicom-routing-update", "{\"id\":1,\"hospitalId\":1,\"dicomServerId\":1,\"routes\":[{\"id\":1,\"machineId\":1,\"modalityId\":3}]}");

        return examples;
    }
}

