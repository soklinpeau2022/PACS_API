package com.ut.emrPacs.model.dto.response.pacs.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class PacsResultResponse {
    @JsonIgnore
    private Long id;
    private String resultKey;
    @JsonIgnore
    private Long hospitalId;
    @JsonIgnore
    private Long modalityId;
    @JsonIgnore
    private Long studyId;
    @JsonIgnore
    private Long worklistId;
    private String worklistCode;
    private String studyInstanceUid;
    private String accessionNumber;
    @JsonIgnore
    private Long patientId;
    private String patientCode;
    private String patientName;
    private String patientPhone;
    private String resultDate;
    @JsonIgnore
    private Long templateId;
    private String templateKey;
    private String templateName;
    private String resultText;
    private String status;
    private Boolean completed;
    @JsonIgnore
    private Long createdBy;
    private String createdAt;
    private String updatedAt;
    private List<PacsResultImageResponse> images;
}
