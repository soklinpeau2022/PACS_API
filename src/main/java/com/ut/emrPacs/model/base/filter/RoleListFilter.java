package com.ut.emrPacs.model.base.filter;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class RoleListFilter extends Filter {
    @Schema(hidden = true)
    private Boolean strictHospitalOnly;
    @Schema(hidden = true)
    private Boolean hideCrossHospitalScopeGroup;
}
