package com.ut.emrPacs.model.dto.response.pacs.dicom;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DicomServerHealthSummaryResponse {
    private Boolean enabled;
    private Integer pollIntervalSeconds;
    private Integer totalServers;
    private Integer onlineServers;
    private Integer offlineServers;
    private Integer unknownServers;
    private String status;
    private String checkedAt;
}
