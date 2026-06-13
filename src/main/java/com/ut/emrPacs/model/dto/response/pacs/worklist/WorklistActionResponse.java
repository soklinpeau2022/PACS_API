package com.ut.emrPacs.model.dto.response.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class WorklistActionResponse {
    @JsonIgnore
    private Long worklistId;
    private String publicKey;
    private String visitCode;
    private String studyUuid;
    private String status;
    private String message;
}
