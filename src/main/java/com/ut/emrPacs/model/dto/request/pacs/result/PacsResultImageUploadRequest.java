package com.ut.emrPacs.model.dto.request.pacs.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PacsResultImageUploadRequest {
    private String resultKey;
    @Schema(hidden = true)
    @Positive
    @JsonIgnore
    private Long resultId;
}
