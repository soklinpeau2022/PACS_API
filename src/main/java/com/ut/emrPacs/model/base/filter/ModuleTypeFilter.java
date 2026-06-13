package com.ut.emrPacs.model.base.filter;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ModuleTypeFilter extends Filter {

    private Long roleId;
}
