package com.ut.emrPacs.model.dto.request.pacs.result;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class PacsResultImageRequest {
    private String imageKey;
    @Schema(hidden = true)
    @JsonIgnore
    private Long imageId;
}
