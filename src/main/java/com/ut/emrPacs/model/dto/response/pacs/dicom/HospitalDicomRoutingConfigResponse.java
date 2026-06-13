package com.ut.emrPacs.model.dto.response.pacs.dicom;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class HospitalDicomRoutingConfigResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;
    private String hospitalName;
    @JsonIgnore
    private Long dicomServerId;
    private String dicomServerPublicKey;
    private String dicomServerName;
    private Long routeCount;
    private Long modalityCount;
    private Long dicomServerCount;
    private Long isActive;
    private String createdAt;
    private String modifiedAt;

    @JsonIgnore
    private String modalityIdCsv;
    @JsonIgnore
    private String modalityPublicKeyCsv;
    @JsonIgnore
    private String modalityNameCsv;
    @JsonIgnore
    private String dicomServerIdCsv;
    @JsonIgnore
    private String dicomServerPublicKeyCsv;
    @JsonIgnore
    private String dicomServerNameCsv;
    @JsonIgnore
    private String dicomServerBaseUrlCsv;
    @JsonIgnore
    private String aeTitleCsv;
    @JsonIgnore
    private String machineNameCsv;
    @JsonIgnore
    private String machineAeTitleCsv;
    @JsonIgnore
    private String machineHostCsv;
    @JsonIgnore
    private String machinePortCsv;

    @JsonIgnore
    private List<Long> modalityIds = new ArrayList<>();
    private List<String> modalityPublicKeys = new ArrayList<>();
    private List<String> modalityNames = new ArrayList<>();
    @JsonIgnore
    private List<Long> dicomServerIds = new ArrayList<>();
    private List<String> dicomServerPublicKeys = new ArrayList<>();
    private List<String> dicomServerNames = new ArrayList<>();
    private List<String> dicomServerBaseUrls = new ArrayList<>();
    private List<String> aeTitles = new ArrayList<>();
    private List<String> machineNames = new ArrayList<>();
    private List<String> machineAeTitles = new ArrayList<>();
    private List<String> machineHosts = new ArrayList<>();
    private List<Integer> machinePorts = new ArrayList<>();
    private List<HospitalModalityServerRouteResponse> routes = new ArrayList<>();
}
