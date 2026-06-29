package com.ut.emrPacs.model.dto.request.application;

import lombok.Data;

@Data
public class ApplicationBrandSettingsRequest {
    private String appName;
    private String logoUrl;
    private String loginBackgroundUrl;
}
