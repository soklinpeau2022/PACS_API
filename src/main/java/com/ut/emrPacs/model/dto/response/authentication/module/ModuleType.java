package com.ut.emrPacs.model.dto.response.authentication.module;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ut.emrPacs.model.role.Module;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class ModuleType implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private Long id;

    private String publicKey;

    @JsonIgnore
    private String code;

    private String name;

    private String nameOther;

    private String groupCode;

    private String groupName;

    private Integer groupOrderNo;

    private List<Module> moduleList;
}
