package com.ut.emrPacs.model.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class BaseModel {

    private String created;

    private Long createdBy;

    private String modified;

    private Long modifiedBy;

    private int isActive;

    private int status;

}