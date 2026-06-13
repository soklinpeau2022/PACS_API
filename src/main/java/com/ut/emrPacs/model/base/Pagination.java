package com.ut.emrPacs.model.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Pagination {

    private Integer page;

    private Integer rowsPerPage;

    private Long total;

}