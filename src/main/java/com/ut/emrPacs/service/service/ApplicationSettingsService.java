package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.dto.request.application.ApplicationBrandSettingsRequest;
import com.ut.emrPacs.model.dto.response.application.ApplicationBrandAssetResponse;
import com.ut.emrPacs.model.dto.response.application.ApplicationBrandSettingsResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface ApplicationSettingsService {
    ApplicationBrandSettingsResponse getBrandSettings();

    ApplicationBrandSettingsResponse updateBrandSettings(ApplicationBrandSettingsRequest request, Long modifiedBy);

    ApplicationBrandAssetResponse uploadBrandAsset(MultipartFile file);

    ResponseEntity<Resource> readBrandAsset(String filename);
}
