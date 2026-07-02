package com.ut.emrPacs.model.dto.request.pacs.dicom;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HospitalDicomServerRequestUpdate {
    @JsonIgnore
    private Long id;
    @JsonAlias({"key", "uuid"})
    private String publicKey;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalKey;
    @Size(max = 255)
    private String name;
    @Size(max = 255)
    private String ipAddress;
    /**
     * DICOMweb / DicomServer REST port, for example 8042.
     */
    private Integer port;
    /**
     * When true, the public DICOM server URL includes the DICOMweb port.
     * Turn off for reverse-proxy deployments that expose the server on the
     * protocol default port.
     */
    private Boolean publicEndpointIncludePort;
    /**
     * Native DICOM C-FIND/C-STORE port, for example 4242.
     */
    @Size(max = 255)
    private String dicomHost;
    private Integer dicomPort;
    @Size(max = 64)
    private String aeTitle;
    @JsonAlias({"dicom_web_path"})
    @Size(max = 255)
    private String dicomwebPath;
    @JsonAlias({"public_health_check_url", "healthCheckUrl", "pingUrl", "publicPingUrl"})
    @Size(max = 1024)
    private String publicHealthCheckUrl;
    @Size(max = 1024)
    private String viewerBaseUrl;
    @JsonAlias({"pacsApiAuthCallback", "pacs_api_callback_base_url"})
    @Size(max = 1024)
    private String pacsApiCallbackBaseUrl;
    @Size(max = 150)
    private String username;
    @Size(max = 255)
    private String password;
    private Long isActive;

    @Size(max = 1024)
    private String storageDirectory;
    @Size(max = 1024)
    private String indexDirectory;
    private Long maximumStorageSize;
    private Long maximumPatientCount;
    private Boolean remoteAccessAllowed;
    private Boolean httpServerEnabled;
    private Boolean enableHttpCompression;
    private Boolean sslEnabled;
    private Boolean authenticationEnabled;
    private Boolean authorizationEnabled;
    @Size(max = 255)
    private String authorizationRoot;
    @Size(max = 64)
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
    @Size(max = 1024)
    private String worklistsDatabase;
    private String pluginsPaths;
}
