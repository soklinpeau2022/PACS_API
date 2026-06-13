package com.ut.emrPacs.model.dto.request.pacs.dicomServer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class DicomServerFindRequest {
    @JsonProperty("Level")
    private String level;

    @JsonProperty("Query")
    private Map<String, String> query;

    @JsonProperty("Expand")
    private Boolean expand;
}
