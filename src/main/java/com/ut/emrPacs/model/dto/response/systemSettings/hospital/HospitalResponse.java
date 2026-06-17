package com.ut.emrPacs.model.dto.response.systemSettings.hospital;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class HospitalResponse {

    @JsonIgnore
    private Long id;
    private String publicKey;
    private String code;
    private String abbr;
    private String name;
    private String nameKhmer;
    private String timezone;
    private String logoFileName;
    private String logoFileType;
    private Long logoFileSize;
    private Boolean logoAvailable;
    private Boolean deploymentLocked;
    private String packageBuiltAt;
    @JsonIgnore
    private Long createdById;
    private String createdBy;
    private String created;
    @JsonIgnore
    private Long modifiedById;
    private String modifiedBy;
    private String modified;
    private List<HospitalUserList> hospitalUserList;
}
