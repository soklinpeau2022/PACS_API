package com.ut.emrPacs.model.dto.response.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class WorklistRouteAvailabilityResponse {
    @JsonIgnore
    private Long hospitalId;
    @JsonIgnore
    private Long modalityId;
    private Boolean hasActiveRouting;
    private Long routeCount;
    private Long machineCount;
    private Long dicomServerCount;
    private List<WorklistRouteServerOptionResponse> dicomServers;
}
