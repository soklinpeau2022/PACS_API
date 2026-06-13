package com.ut.emrPacs.model.dto.response.pacs.dicom;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class HospitalDicomServerResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;
    private String hospitalName;
    private String name;
    private String ipAddress;
    private Integer port;
    private Integer dicomPort;
    private String aeTitle;
    private String baseUrl;
    private String dicomServerUiBaseUrl;
    private String dicomwebBaseUrl;
    private String dicomwebPath;
    private String publicHealthCheckUrl;
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
    @JsonIgnore
    private List<Long> modalityIds = new ArrayList<>();
    private List<String> modalityPublicKeys = new ArrayList<>();
    private List<String> modalityNames = new ArrayList<>();
    private String healthStatus;
    private Boolean healthOnline;
    private String healthCheckedAt;
    private String healthLastOnlineAt;
    private String healthOfflineSince;
    private Long healthOfflineSeconds;
    private Long healthResponseTimeMs;
    @JsonIgnore
    private String modalityIdCsv;
    @JsonIgnore
    private String modalityPublicKeyCsv;
    @JsonIgnore
    private String modalityNameCsv;
    private Long isActive;
    private String createdAt;
    private String modifiedAt;

    @JsonIgnore
    public List<Long> getModalityIds() {
        return modalityIds;
    }

    @JsonProperty("modalityNames")
    public List<String> getModalityNames() {
        return modalityNames;
    }
}
