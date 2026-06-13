package com.ut.emrPacs.model.dto.response.pacs.dicomServer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DicomServerSeriesResponse {
    @JsonProperty("ID")
    private String id;

    @JsonProperty("Instances")
    private List<String> instances;

    @JsonProperty("MainDicomTags")
    private Map<String, Object> mainDicomTags;
}
