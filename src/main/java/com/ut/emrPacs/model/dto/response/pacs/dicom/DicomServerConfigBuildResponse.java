package com.ut.emrPacs.model.dto.response.pacs.dicom;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Map;

@Data
public class DicomServerConfigBuildResponse {
    @JsonIgnore
    private Long routingConfigId;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;
    private String hospitalName;
    @JsonIgnore
    private Long dicomServerId;
    private String dicomServerPublicKey;
    private String dicomServerName;
    private String fileName;
    private Map<String, Object> config;
    private String environmentFileName;
    private String environmentContent;
    private String callbackScriptFileName;
    private String callbackScriptContent;
    private String setupFileName;
    private String setupContent;
    private String callbackClientId;
    private String projectName;
    private String zipFileName;
    private String zipContentBase64;
}
