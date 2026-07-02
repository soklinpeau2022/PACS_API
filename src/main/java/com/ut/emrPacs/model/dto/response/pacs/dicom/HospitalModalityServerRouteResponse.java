package com.ut.emrPacs.model.dto.response.pacs.dicom;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HospitalModalityServerRouteResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    @JsonIgnore
    private Long routingConfigId;
    private String routingConfigPublicKey;
    @JsonIgnore
    private Long machineId;
    private String machinePublicKey;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;
    private String hospitalName;
    @JsonIgnore
    private Long modalityId;
    private String modalityPublicKey;
    private String modalityName;
    private String modalityAbbr;
    @JsonIgnore
    private Long dicomServerId;
    private String dicomServerPublicKey;
    private String dicomServerName;
    private String machineName;
    private String machineAeTitle;
    private String machineHost;
    private Integer machinePort;
    private String ipAddress;
    private Integer port;
    private Boolean publicEndpointIncludePort;
    private String dicomHost;
    private Integer dicomPort;
    private String aeTitle;
    private String baseUrl;
    private String dicomServerUiBaseUrl;
    private String dicomwebBaseUrl;
    private String dicomwebPath;
    private String viewerBaseUrl;
    private String pacsApiCallbackBaseUrl;
    private String username;
    private Boolean hasPassword;
    @JsonIgnore
    private String password;
    private Boolean hasPacsResultApiKey;
    @JsonIgnore
    private String pacsResultApiKeyHash;
    private String storageDirectory;
    private String indexDirectory;
    private Long maximumStorageSize;
    private Long maximumPatientCount;
    private Boolean remoteAccessAllowed;
    private Boolean httpServerEnabled;
    private Boolean enableHttpCompression;
    private Boolean sslEnabled;
    private Boolean authenticationEnabled;
    private Boolean authorizationEnabled;
    private String authorizationRoot;
    private String authorizationCheckedLevel;
    private Boolean dicomAlwaysAllowEcho;
    private Boolean dicomAlwaysAllowFind;
    private Boolean dicomAlwaysAllowGet;
    private Boolean dicomAlwaysAllowMove;
    private Boolean dicomAlwaysAllowStore;
    private Boolean dicomCheckCalledAet;
    private Boolean dicomTlsEnabled;
    private Integer dicomScpTimeout;
    private String dicomPeersJson;
    private Boolean worklistsEnabled;
    private String worklistsDatabase;
    private String pluginsPaths;
    private Long isActive;

    @JsonIgnore
    public Long getModalityId() {
        return modalityId;
    }

    @JsonProperty("modalityName")
    public String getModalityName() {
        return modalityName;
    }
}
