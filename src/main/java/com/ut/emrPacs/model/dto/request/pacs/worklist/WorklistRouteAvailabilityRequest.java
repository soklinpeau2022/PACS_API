package com.ut.emrPacs.model.dto.request.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class WorklistRouteAvailabilityRequest {
    @JsonIgnore
    private Long hospitalId;
    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    private String hospitalKey;

    @JsonIgnore
    private Long modalityId;
    @JsonAlias({"modalityPublicKey", "modalityUuid", "modalityUUID"})
    private String modalityKey;
}
