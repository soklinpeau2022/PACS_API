package com.ut.emrPacs.model.components.pacs.dicom;

import lombok.Data;

@Data
public class HospitalDicomRoutingConfig {
    private Long id;
    private Long hospitalId;
    private Long dicomServerId;
    private Long isActive;
    private Long createdBy;
    private Long modifiedBy;
}
