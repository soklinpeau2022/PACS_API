package com.ut.emrPacs.model.dto.response.pacs.dicomServer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DicomServerWorklistCreateResponse {
    @JsonProperty("ID")
    private String id;

    @JsonProperty("Path")
    private String path;
}
