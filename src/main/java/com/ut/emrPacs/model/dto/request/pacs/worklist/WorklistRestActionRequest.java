package com.ut.emrPacs.model.dto.request.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class WorklistRestActionRequest {
    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    private String hospitalKey;
    private String notes;
}
