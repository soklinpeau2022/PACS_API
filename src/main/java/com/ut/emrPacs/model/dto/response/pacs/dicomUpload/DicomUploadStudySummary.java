package com.ut.emrPacs.model.dto.response.pacs.dicomUpload;

import lombok.Data;

@Data
public class DicomUploadStudySummary {
    private String studyPublicKey;
    private String patientPublicKey;
    private String patientCode;
    private String firstName;
    private String lastName;
    private String patientBirthDate;
    private String patientSex;
    private Boolean patientCreated;
    private String accessionNumber;
    private String referenceVisitCode;
    private String worklistPublicKey;
    private String worklistVisitCode;
    private Boolean worklistCreated;
    private String studyInstanceUid;
    private String studyDescription;
    private String modality;
    private String modalityPublicKey;
    private String modalityName;
    private String dicomServerStudyId;
    private String dicomServerPatientId;
    private String dicomServerSeriesId;
    private Integer instances;
    private String status;
    private Boolean viewerAvailable;
}
