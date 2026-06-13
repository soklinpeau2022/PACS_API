package com.ut.emrPacs.model.dto.request.pacs.dicom;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HospitalDicomMachineRequestUpdate {
    @JsonIgnore
    private Long id;
    @JsonAlias({"key", "uuid"})
    private String publicKey;

    @JsonIgnore
    private Long hospitalId;
    private String hospitalKey;

    @JsonIgnore
    private Long modalityId;
    private String modalityKey;

    @Size(max = 160)
    private String machineName;

    @Size(max = 64)
    private String machineAeTitle;

    @Size(max = 255)
    private String machineHost;

    @Min(1)
    private Integer machinePort;
}
