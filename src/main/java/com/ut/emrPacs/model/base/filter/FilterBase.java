package com.ut.emrPacs.model.base.filter;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import lombok.Data;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class FilterBase {

    @Min(0)
    private Integer page;

    @Min(0)
    private Integer rowsPerPage;

}
