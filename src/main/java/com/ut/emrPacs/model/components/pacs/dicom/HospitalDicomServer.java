package com.ut.emrPacs.model.components.pacs.dicom;

import lombok.Data;

@Data
public class HospitalDicomServer {
    private Long id;
    private Long hospitalId;
    private String name;
    private String ipAddress;
    private Integer port;
    private Boolean publicEndpointIncludePort;
    private String dicomHost;
    private Integer dicomPort;
    private String aeTitle;
    private String dicomwebPath;
    private String publicHealthCheckUrl;
    private String viewerBaseUrl;
    private String pacsApiCallbackBaseUrl;
    private String username;
    private String password;
    private String pacsResultApiKeyHash;
    private Long isActive;
    private Long createdBy;
    private Long modifiedBy;

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
}
