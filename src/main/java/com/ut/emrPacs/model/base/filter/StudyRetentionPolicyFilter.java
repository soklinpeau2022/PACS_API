package com.ut.emrPacs.model.base.filter;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class StudyRetentionPolicyFilter extends Filter {
    @JsonIgnore
    @Schema(hidden = true)
    private Long dicomServerId;

    @JsonAlias({"dicomServerPublicKey", "dicomServerUuid", "dicomServerUUID"})
    private String dicomServerKey;

    @JsonIgnore
    @Schema(hidden = true)
    private Long modalityId;

    @JsonAlias({"modalityPublicKey", "modalityUuid", "modalityUUID"})
    private String modalityKey;

    private Boolean enabled;

    public void setDicomServerKey(String dicomServerKey) {
        this.dicomServerKey = trimToNull(dicomServerKey);
    }

    public void setModalityKey(String modalityKey) {
        this.modalityKey = trimToNull(modalityKey);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
