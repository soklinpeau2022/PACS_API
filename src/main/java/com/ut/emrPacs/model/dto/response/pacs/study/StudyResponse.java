package com.ut.emrPacs.model.dto.response.pacs.study;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StudyResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;
    @JsonIgnore
    private Long patientId;
    private String patientName;
    private String mrn;
    private String studyInstanceUid;
    private String accessionNumber;
    private String referenceVisitCode;
    private String modality;
    private LocalDate studyDate;
    private String studyDateTime;
    private String studyDescription;
    @JsonIgnore
    private Long dicomServerId;
    private String status;
    private Integer instances;
    private String dicomServerStudyId;
    private String dicomServerPatientId;
    private String dicomServerSeriesId;
    private String viewerUrl;
    @JsonIgnore
    private Long worklistId;
    private String worklistPublicKey;
    private String worklistVisitCode;
    private String worklistStatus;
    private String imageReceivedAt;
}
