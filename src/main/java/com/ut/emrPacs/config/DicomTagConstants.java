package com.ut.emrPacs.config;

/**
 * DICOM attribute keyword names used in DICOMweb query parameters and
 * JSON payloads exchanged with UDAYA_DICOM_SERVER.
 */
public final class DicomTagConstants {

    private DicomTagConstants() {
    }

    public static final String STUDY_INSTANCE_UID = "StudyInstanceUID";
    public static final String ACCESSION_NUMBER = "AccessionNumber";
    public static final String STUDY_DESCRIPTION = "StudyDescription";
    public static final String STUDY_DATE = "StudyDate";
    public static final String PATIENT_NAME = "PatientName";
}
