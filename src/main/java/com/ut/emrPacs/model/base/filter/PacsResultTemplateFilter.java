package com.ut.emrPacs.model.base.filter;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PacsResultTemplateFilter extends Filter {
    @JsonIgnore
    @Schema(hidden = true)
    private Long modalityId;

    @JsonAlias({"modalityPublicKey", "modalityUuid", "modalityUUID"})
    @Schema(description = "Public modality key.", example = "00000000-0000-0000-0000-000000000000")
    private String modalityKey;

    public void setModalityKey(String modalityKey) {
        if (modalityKey == null) {
            this.modalityKey = null;
            return;
        }
        String trimmed = modalityKey.trim();
        this.modalityKey = trimmed.isEmpty() ? null : trimmed;
    }
}
