package com.ut.emrPacs.model.dto.response.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class WorklistMachineRouteChoiceResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    @JsonIgnore
    private Long modalityId;
    private String modalityPublicKey;
    private String modalityName;
    private String modalityCode;
    private String machinePublicKey;
    private String machineName;
    private String dicomServerPublicKey;
    private String dicomServerName;
}
