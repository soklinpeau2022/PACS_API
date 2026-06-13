package com.ut.emrPacs.model.dto.response.pacs.dicomServer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DicomServerInstanceUploadResponse {
    @JsonProperty("ID")
    private String id;

    @JsonProperty("ParentPatient")
    private String parentPatient;

    @JsonProperty("ParentStudy")
    private String parentStudy;

    @JsonProperty("ParentSeries")
    private String parentSeries;

    @JsonProperty("Path")
    private String path;

    @JsonProperty("Status")
    private String status;
}
