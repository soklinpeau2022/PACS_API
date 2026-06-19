package com.ut.emrPacs.model.dto.response.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class WorklistListResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;
    private String hospitalCode;
    private String hospitalName;
    @JsonIgnore
    private Long patientId;
    private String patientPublicKey;
    private String patientUid;
    private String patientHn;
    private String patientName;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dob;
    private String sex;
    @JsonIgnore
    private Long modalityId;
    private String modalityPublicKey;
    private String modalityName;
    @JsonIgnore
    private Long dicomServerId;
    @JsonIgnore
    private Long dicomRouteId;
    private String visitCode;
    private String studyUuid;
    private Integer instances;
    private String status;
    private String createdAt;
    private String studyDescription;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate scheduledDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    private LocalTime scheduledTime;
    @JsonIgnore
    private Long studyId;
    private String studyPublicKey;
    private WorklistItemRefResponse modality;

    private String accessionNumber;
    private String referenceVisitCode;
    private String dicomServerWorklistId;
    private String dicomServerWorklistPath;
    private String dicomServerStudyId;
    private String studyInstanceUid;
    private String dicomServerPatientId;
    private String dicomServerSeriesId;
    private String viewerUrl;
    private String sentAt;
    private String receivedAt;
    private String startedAt;
    private String imageReceivedAt;
    private String institutionName;
    private String cancelledAt;

    @JsonIgnore
    public Long getModalityId() {
        return modalityId;
    }

    @JsonProperty("modalityName")
    public String getModalityName() {
        return modalityName;
    }

    @JsonProperty("modality")
    public WorklistItemRefResponse getModality() {
        return modality;
    }
}
