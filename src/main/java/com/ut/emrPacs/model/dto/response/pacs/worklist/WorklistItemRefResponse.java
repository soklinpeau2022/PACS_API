package com.ut.emrPacs.model.dto.response.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class WorklistItemRefResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    private String name;
}
