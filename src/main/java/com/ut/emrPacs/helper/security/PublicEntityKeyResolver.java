package com.ut.emrPacs.helper.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class PublicEntityKeyResolver {
    public enum Entity {
        PATIENT,
        WORKLIST,
        STUDY,
        HOSPITAL,
        MODALITY,
        USER,
        ROLE,
        DICOM_SERVER,
        DICOM_MACHINE,
        DICOM_ROUTING_CONFIG,
        DICOM_ROUTE,
        MODULE_TYPE,
        MODULE,
        MODULE_DETAIL,
        PACS_RESULT_TEMPLATE,
        STUDY_RETENTION_POLICY,
        SYSTEM_ACTIVITY,
        USER_LOG
    }

    private static final Map<Entity, String> TABLES = new EnumMap<>(Entity.class);

    static {
        TABLES.put(Entity.PATIENT, "patients");
        TABLES.put(Entity.WORKLIST, "pacs_worklists");
        TABLES.put(Entity.STUDY, "pacs_studies");
        TABLES.put(Entity.HOSPITAL, "hospitals");
        TABLES.put(Entity.MODALITY, "modalities");
        TABLES.put(Entity.USER, "users");
        TABLES.put(Entity.ROLE, "roles");
        TABLES.put(Entity.DICOM_SERVER, "hospital_dicom_servers");
        TABLES.put(Entity.DICOM_MACHINE, "hospital_dicom_machines");
        TABLES.put(Entity.DICOM_ROUTING_CONFIG, "hospital_dicom_routing_configs");
        TABLES.put(Entity.DICOM_ROUTE, "hospital_modality_server_routes");
        TABLES.put(Entity.MODULE_TYPE, "module_types");
        TABLES.put(Entity.MODULE, "modules");
        TABLES.put(Entity.MODULE_DETAIL, "module_details");
        TABLES.put(Entity.PACS_RESULT_TEMPLATE, "pacs_result_templates");
        TABLES.put(Entity.STUDY_RETENTION_POLICY, "study_retention_policies");
        TABLES.put(Entity.SYSTEM_ACTIVITY, "system_activities");
        TABLES.put(Entity.USER_LOG, "user_logs");
    }

    private final JdbcTemplate jdbcTemplate;

    public PublicEntityKeyResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long resolveRequired(Entity entity, String publicKey, Long fallbackId, String label) {
        Long id = resolve(entity, publicKey, fallbackId);
        if (id == null || id <= 0) {
            throw new IllegalArgumentException((label == null || label.isBlank() ? "Record" : label) + " not found.");
        }
        return id;
    }

    public Long resolve(Entity entity, String publicKey, Long fallbackId) {
        if (publicKey == null || publicKey.isBlank()) {
            return normalizeLegacyId(fallbackId);
        }

        String token = publicKey.trim();
        UUID uuid = tryParseUuid(token);
        if (uuid == null) {
            return null;
        }

        String table = Objects.requireNonNull(TABLES.get(entity), "Unsupported public entity.");
        String sql = "SELECT id FROM " + table + " WHERE public_id = CAST(? AS uuid) LIMIT 1";
        return jdbcTemplate.query(sql, ps -> ps.setString(1, uuid.toString()), rs -> rs.next() ? rs.getLong(1) : null);
    }

    public Long resolveFromPath(Entity entity, String pathToken, String label) {
        return resolveRequired(entity, pathToken, null, label);
    }

    private static Long normalizeLegacyId(Long fallbackId) {
        return fallbackId != null && fallbackId > 0 ? fallbackId : null;
    }

    private static UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
