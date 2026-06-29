package com.ut.emrPacs.model.dto.response.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplicationBrandAssetResponse {
    private String assetUrl;
    private String filename;
}
