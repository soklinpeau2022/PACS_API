ALTER TABLE hospital_dicom_routing_configs
    ADD COLUMN IF NOT EXISTS package_built_at TIMESTAMPTZ;

ALTER TABLE hospital_dicom_routing_configs
    ADD COLUMN IF NOT EXISTS package_built_by BIGINT;

CREATE INDEX IF NOT EXISTS idx_hdrc_package_built_hospital
    ON hospital_dicom_routing_configs (hospital_id, package_built_at DESC)
    WHERE package_built_at IS NOT NULL;

COMMENT ON COLUMN hospital_dicom_routing_configs.package_built_at IS
    'First/current time a UDAYA_DICOM_SERVER deployment package was generated. Hospitals with any built package keep identity fields locked.';

COMMENT ON COLUMN hospital_dicom_routing_configs.package_built_by IS
    'User id that most recently generated the UDAYA_DICOM_SERVER deployment package.';
