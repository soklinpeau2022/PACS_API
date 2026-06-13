package com.ut.emrPacs.model.dto.request.authentication.userGroup;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ut.emrPacs.helper.sql.SqlSanitizerHelper;
import com.ut.emrPacs.model.base.filter.FilterBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserGroupListRequest extends FilterBase {

    private static final int MAX_SEARCH_TEXT_LENGTH = 120;

    @Schema(description = "Search by group name or hospital name.")
    private String searchText;

    @Schema(description = "Safe order by expression. Example: r.id DESC")
    private String orderBy;

    @Schema(description = "Optional hospital filter. Effective for super admin; normal users are forced to login hospital.")
    @JsonIgnore
    private Long hospitalId;
    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    private String hospitalKey;

    public void setSearchText(String searchText) {
        if (searchText == null) {
            this.searchText = null;
            return;
        }
        String trimmed = searchText.trim();
        if (trimmed.isEmpty()) {
            this.searchText = null;
            return;
        }
        this.searchText = trimmed.length() > MAX_SEARCH_TEXT_LENGTH
                ? trimmed.substring(0, MAX_SEARCH_TEXT_LENGTH)
                : trimmed;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = SqlSanitizerHelper.sanitizeOrderBy(orderBy);
    }
}
