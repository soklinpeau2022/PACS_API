package com.ut.emrPacs.model.log;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ut.emrPacs.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;


@EqualsAndHashCode(callSuper = true)
@Data
public class ActivityLog extends BaseModel {

    @JsonIgnore
    private Long id;

    private String publicKey;

    private String module;

    private Long moduleId;

    private String act;

    private String action;

    private String actionName;

    private String description;

    private String bug;

    private String browser;

    private String operatingSystem;

    private String ip;

    private Long duration;

    private String hostName;

    private Long line;

    private String endpoint;

    private String username;

    private String createdAt;

}
