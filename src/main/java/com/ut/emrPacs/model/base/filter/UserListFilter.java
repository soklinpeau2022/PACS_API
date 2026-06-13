package com.ut.emrPacs.model.base.filter;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserListFilter extends Filter {
    @Schema(description = "Hospital filter. For super admin (user id=1): optional. For other users: ignored and forced to current login hospital.")
    private Long hospitalId;
}
