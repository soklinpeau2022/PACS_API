package com.ut.emrPacs.model.components.systemSettings.modality;

import lombok.Data;

@Data
public class Modality {
    private Long id;
    private String abbr;
    private String name;
    private Long isActive;
    private Long createdBy;
    private Long modifiedBy;
}
