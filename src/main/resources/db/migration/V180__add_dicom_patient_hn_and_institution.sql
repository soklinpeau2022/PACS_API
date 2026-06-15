ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS patient_hn VARCHAR(100);

ALTER TABLE pacs_studies
    ADD COLUMN IF NOT EXISTS institution_name VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_patients_hospital_lower_patient_hn_active
    ON patients (hospital_id, LOWER(patient_hn))
    WHERE is_active = 1
      AND patient_hn IS NOT NULL
      AND BTRIM(patient_hn) <> '';

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_lower_institution_active
    ON pacs_studies (hospital_id, LOWER(institution_name))
    WHERE is_active = 1
      AND institution_name IS NOT NULL
      AND BTRIM(institution_name) <> '';
