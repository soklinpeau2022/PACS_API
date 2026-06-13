package com.ut.emrPacs.model.dto.response.dropDown;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class DicomServerDropDownResponse {

    @JsonIgnore
    private Long value;
    @JsonIgnore
    private Long id;
    private String publicKey;
    private String label;
    private String name;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;
    private String hospitalName;
    private String ipAddress;
    private Integer port;
    private Integer dicomPort;
    private String dicomServerBaseUrl;
    private String dicomServerUiBaseUrl;
    private String dicomwebBaseUrl;
    private String dicomwebPath;
    private String viewerBaseUrl;
    private String aeTitle;
    private String username;
    private Integer isActive;
}
