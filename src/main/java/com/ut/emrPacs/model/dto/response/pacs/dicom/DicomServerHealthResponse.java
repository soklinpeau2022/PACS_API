package com.ut.emrPacs.model.dto.response.pacs.dicom;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DicomServerHealthResponse {
    private String publicKey;
    private String name;
    private String hospitalPublicKey;
    private String hospitalName;
    private String ipAddress;
    private String publicHealthCheckUrl;
    private Integer port;
    private Integer dicomPort;
    private String aeTitle;
    private String status;
    private Boolean online;
    private String checkedAt;
    private String lastOnlineAt;
    private String offlineSince;
    private Long offlineSeconds;
    private Long responseTimeMs;
}
