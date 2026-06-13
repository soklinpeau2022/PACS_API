-- Remove the retired PACS Service module. Queue scheduling now uses patient,
-- modality, DICOM routing, and study description directly.

DELETE FROM role_module_details rmd
USING module_details md
WHERE rmd.module_detail_id = md.id
  AND md.code IN ('service.view', 'service.add', 'service.edit', 'service.delete');

DELETE FROM module_details
WHERE code IN ('service.view', 'service.add', 'service.edit', 'service.delete')
   OR code = 'pacs-service';

DELETE FROM endpoint_permissions
WHERE permission_code IN ('service.view', 'service.add', 'service.edit', 'service.delete')
   OR endpoint_pattern LIKE '/service/%'
   OR endpoint_pattern IN ('/dropdown/dropdown-service', '/dropdown/dropdown-service-by-modality');

UPDATE module_types
SET is_active = 2,
    modified_at = NOW()
WHERE code IN ('PACS_SERVICE', 'pacs-service')
   OR LOWER(name) = 'service';

DROP INDEX IF EXISTS idx_patient_queue_service_status_created_at;
DROP INDEX IF EXISTS idx_queue_hospital_service_id_desc;
DROP INDEX IF EXISTS idx_hospital_modality_services_active;
DROP INDEX IF EXISTS idx_hospital_modality_services_service;
DROP INDEX IF EXISTS ux_services_name_active;
DROP INDEX IF EXISTS ux_services_code_active;
DROP INDEX IF EXISTS ux_services_abbr_active;
DROP INDEX IF EXISTS idx_services_active_id_desc;
DROP INDEX IF EXISTS idx_services_created_by;
DROP INDEX IF EXISTS idx_services_modified_by;

ALTER TABLE pacs_patient_queue DROP CONSTRAINT IF EXISTS pacs_patient_queue_service_id_fkey;

DROP TABLE IF EXISTS hospital_modality_services CASCADE;

ALTER TABLE pacs_patient_queue DROP COLUMN IF EXISTS service_id;

DROP TABLE IF EXISTS services CASCADE;

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified_at = NOW()
WHERE is_active = 1;