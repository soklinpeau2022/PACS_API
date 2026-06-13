package com.ut.emrPacs.model.dto.request.pacs.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PacsResultTemplateListRequest {
    private String hospitalKey;
    @Positive
    @JsonIgnore
    private Long hospitalId;

    private String modalityKey;
    @Positive
    @JsonIgnore
    private Long modalityId;
}
