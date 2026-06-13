package com.ut.emrPacs.model.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.ut.emrPacs.model.dto.response.authentication.module.MenuGroupResponse;
import lombok.Data;

import java.util.List;
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"message", "status", "pagination", "menuGroupList", "data", "id"})
@Data
public class BaseResult {

    @JsonIgnore
    private Boolean status;

    private String message;

    private Pagination pagination;

    private List<MenuGroupResponse> menuGroupList;

    private List<?> data;

    private Long id;

}
