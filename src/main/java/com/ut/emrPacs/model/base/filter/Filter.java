package com.ut.emrPacs.model.base.filter;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.ut.emrPacs.helper.sql.SqlSanitizerHelper;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class Filter extends FilterBase {

    private static final int MAX_SEARCH_TEXT_LENGTH = 120;

    private String orderBy;

    private String searchText;

    @Schema(hidden = true)
    private Long hospitalId;

    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    @Schema(description = "Public hospital key. Preferred by frontend URLs and filters.", example = "00000000-0000-0000-0000-000000000000")
    private String hospitalKey;

    @Schema(hidden = true)
    private Long userId;

    public void setOrderBy(String orderBy) {
        this.orderBy = SqlSanitizerHelper.sanitizeOrderBy(orderBy);
    }

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

    public void setHospitalKey(String hospitalKey) {
        if (hospitalKey == null) {
            this.hospitalKey = null;
            return;
        }
        String trimmed = hospitalKey.trim();
        this.hospitalKey = trimmed.isEmpty() ? null : trimmed;
    }

}
