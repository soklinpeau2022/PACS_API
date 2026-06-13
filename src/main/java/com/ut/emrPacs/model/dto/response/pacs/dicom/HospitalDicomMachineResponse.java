package com.ut.emrPacs.model.dto.response.pacs.dicom;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class HospitalDicomMachineResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;
    private String hospitalName;
    @JsonIgnore
    private Long modalityId;
    private String modalityPublicKey;
    private String modalityName;
    private String modalityAbbr;
    private String machineName;
    private String machineAeTitle;
    private String machineHost;
    private Integer machinePort;
    private Long isActive;
    private String createdAt;
    private String modifiedAt;
}
