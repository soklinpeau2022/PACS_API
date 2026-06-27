-- Revert placeholder patient birth dates back to NULL.
-- A missing DICOM Patient Birth Date (0010,0030), and the one-time V42 backfill,
-- previously stored 1900-01-01 as a stand-in. That is a placeholder, not a real
-- date of birth: NULL is the correct "unknown DOB" value and is consistent with
-- the fixed DICOM upload mapping (which now leaves a missing DOB NULL) and the
-- null-safe demographics matching in PatientMapper.findByDemographics.
UPDATE patients
SET date_of_birth = NULL,
    modified = NOW()
WHERE date_of_birth = DATE '1900-01-01';
