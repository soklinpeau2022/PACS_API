DROP INDEX IF EXISTS ux_hdm_active_machine_code;
DROP INDEX IF EXISTS idx_hdm_machine_lookup;

CREATE UNIQUE INDEX IF NOT EXISTS ux_hdm_active_machine_endpoint
    ON hospital_dicom_machines (
        hospital_id,
        modality_id,
        LOWER(machine_ae_title),
        LOWER(machine_host),
        machine_port
    )
    WHERE is_active = 1;

ALTER TABLE hospital_dicom_machines
    DROP COLUMN IF EXISTS machine_code;

COMMENT ON TABLE hospital_dicom_machines IS 'Reusable physical modality machines and rooms per hospital. A machine is identified by hospital, modality, AE title, host, and DICOM port.';
