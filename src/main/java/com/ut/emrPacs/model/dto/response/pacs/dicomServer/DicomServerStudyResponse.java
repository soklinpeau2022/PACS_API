package com.ut.emrPacs.model.dto.response.pacs.dicomServer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DicomServerStudyResponse {
    @JsonProperty("ID")
    private String id;

    @JsonProperty("ParentPatient")
    private String parentPatient;

    @JsonProperty("MainDicomTags")
    private Map<String, Object> mainDicomTags;

    @JsonProperty("PatientMainDicomTags")
    private Map<String, Object> patientMainDicomTags;

    @JsonProperty("Instances")
    private List<String> instances;

    @JsonProperty("Series")
    private List<String> series;

    @JsonProperty("Statistics")
    private Map<String, Object> statistics;
}
