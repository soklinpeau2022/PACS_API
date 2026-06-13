package com.ut.emrPacs.model.dto.response.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class WorklistRouteServerOptionResponse {
    @JsonIgnore
    private Long dicomServerId;
    private String dicomServerPublicKey;
    private String dicomServerName;
    private Long routeCount;
    private Long machineCount;
}
