package com.ut.emrPacs.model.role;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ut.emrPacs.model.base.BaseModel;
import com.ut.emrPacs.model.dto.response.authentication.module.ModuleType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class Role extends BaseModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private Long id;

    @JsonIgnore
    private Long hospitalId;

    private String name;

    private String modifiedDate;

    private List<RoleUser> userList;

    private List<ModuleType> moduleTypeList;

}
