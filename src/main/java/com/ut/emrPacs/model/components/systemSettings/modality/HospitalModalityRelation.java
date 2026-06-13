package com.ut.emrPacs.model.components.systemSettings.modality;

import lombok.Data;

@Data
public class HospitalModalityRelation {
    private Long hospitalId;
    private Long modalityId;
    private Long createdBy;
    private Long modifiedBy;
}
