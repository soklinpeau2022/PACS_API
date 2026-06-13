package com.ut.emrPacs.model.components.pacs.dicom;

import lombok.Data;

@Data
public class HospitalDicomMachine {
    private Long id;
    private Long hospitalId;
    private Long modalityId;
    private String machineName;
    private String machineAeTitle;
    private String machineHost;
    private Integer machinePort;
    private Long isActive;
    private Long createdBy;
    private Long modifiedBy;
}
