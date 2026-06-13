package com.ut.emrPacs.model.components.systemSettings.hospital;

import lombok.Data;

@Data
public class Hospital {

    private Long id;
    private String code;
    private String abbr;
    private String name;
    private String nameOther;
    private String timezone;
    private Long createdBy;
    private String createdDate;
    private Long modifiedBy;
    private String modified;
    private String logoPath;
    private String logoFileName;
    private String logoFileType;
    private Long logoFileSize;
}
