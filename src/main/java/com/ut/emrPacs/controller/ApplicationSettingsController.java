package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.authentication.util.AuthorityUtils;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.application.ApplicationBrandSettingsRequest;
import com.ut.emrPacs.service.service.ApplicationSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@Validated
@RequestMapping(ApiConstants.ApplicationSettings.BASE_PATH)
@Tag(name = "01. Application Settings Controller", description = "Global application branding settings.")
public class ApplicationSettingsController {

    private final ApplicationSettingsService applicationSettingsService;
    private final MessageService messageService;

    public ApplicationSettingsController(
            ApplicationSettingsService applicationSettingsService,
            MessageService messageService
    ) {
        this.applicationSettingsService = applicationSettingsService;
        this.messageService = messageService;
    }

    @GetMapping(ApiConstants.ApplicationSettings.BRAND_PUBLIC_GET_PATH)
    @Operation(summary = "Get public application brand settings", description = "Public endpoint used by login and app shell branding.")
    public ResponseMessage<BaseResult> getPublicBrandSettings() {
        var settings = applicationSettingsService.getBrandSettings();
        return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(settings), true));
    }

    @GetMapping(ApiConstants.ApplicationSettings.BRAND_ASSET_PATH)
    @Operation(summary = "Read public brand image asset", description = "Streams a server-hosted logo or login background image.")
    public ResponseEntity<Resource> readBrandAsset(@PathVariable String filename) {
        return applicationSettingsService.readBrandAsset(filename);
    }

    @PostMapping(ApiConstants.ApplicationSettings.BRAND_UPDATE_PATH)
    @Operation(summary = "Update application brand settings", description = "Updates the global application name, logo, and login background.")
    public ResponseMessage<BaseResult> updateBrandSettings(
            @RequestBody(required = false) ApplicationBrandSettingsRequest request
    ) {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        if (!isSuperAdmin(principal.userId())) {
            return ResponseMessageUtils.makeResponse(false, 403, "Forbidden", "Only Super Admin can update global branding.");
        }
        try {
            var settings = applicationSettingsService.updateBrandSettings(request, principal.userId());
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(settings), true));
        } catch (IllegalArgumentException error) {
            return ResponseMessageUtils.makeResponse(false, messageService.message(error.getMessage(), false));
        }
    }

    @PostMapping(value = ApiConstants.ApplicationSettings.BRAND_LOGO_UPLOAD_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload application logo", description = "Uploads a logo image and returns a server asset URL.")
    public ResponseMessage<BaseResult> uploadLogo(@RequestPart(name = "file", required = false) MultipartFile file) {
        return uploadBrandAsset(file);
    }

    @PostMapping(value = ApiConstants.ApplicationSettings.BRAND_BACKGROUND_UPLOAD_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload login background", description = "Uploads a login background image and returns a server asset URL.")
    public ResponseMessage<BaseResult> uploadLoginBackground(@RequestPart(name = "file", required = false) MultipartFile file) {
        return uploadBrandAsset(file);
    }

    private ResponseMessage<BaseResult> uploadBrandAsset(MultipartFile file) {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        if (!isSuperAdmin(principal.userId())) {
            return ResponseMessageUtils.makeResponse(false, 403, "Forbidden", "Only Super Admin can update global branding.");
        }
        try {
            var asset = applicationSettingsService.uploadBrandAsset(file);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(asset), true));
        } catch (IllegalArgumentException error) {
            return ResponseMessageUtils.makeResponse(false, messageService.message(error.getMessage(), false));
        } catch (RuntimeException error) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to save brand image.", false));
        }
    }

    private static boolean isSuperAdmin(Long userId) {
        return Long.valueOf(1L).equals(userId) || AuthorityUtils.isCrossHospitalAdminUser();
    }
}
