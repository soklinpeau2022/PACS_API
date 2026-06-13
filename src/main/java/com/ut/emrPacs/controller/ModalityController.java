package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.ModalityFilter;
import com.ut.emrPacs.model.dto.request.systemSettings.modality.ModalityRequestUpdate;
import com.ut.emrPacs.service.service.ModalityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.UnknownHostException;

@RestController
@Validated
@RequestMapping(ApiConstants.Modality.BASE_PATH)
@Tag(name = "4. Modality Controller", description = "Endpoints for managing modalities")
public class ModalityController {

    @Autowired
    private ModalityService modalityService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.Modality.LIST_PATH)
    @Operation(summary = "List modalities", description = "Module -> Modality. Endpoint -> POST /modality/modality-list")
    public ResponseMessage<BaseResult> listModality(@Valid @RequestBody ModalityFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return modalityService.listModality(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Modality.FIND_PATH)
    @Operation(summary = "Find modality by ID", description = "Module -> Modality. Endpoint -> POST /modality/modality-find/{id}")
    public ResponseMessage<BaseResult> getModalityById(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return modalityService.getModalityById(publicEntityKeyResolver.resolveFromPath(Entity.MODALITY, id, "Modality"), httpServletRequest);
    }

    @PostMapping(ApiConstants.Modality.CREATE_PATH)
    @Operation(summary = "Create modality", description = "Module -> Modality. Endpoint -> POST /modality/modality-create")
    public ResponseMessage<BaseResult> createModality(@Valid @RequestBody ModalityRequestUpdate requestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return modalityService.createModality(requestUpdate, httpServletRequest);
    }

    @PostMapping(ApiConstants.Modality.UPDATE_PATH)
    @Operation(summary = "Update modality", description = "Module -> Modality. Endpoint -> POST /modality/modality-update")
    public ResponseMessage<BaseResult> updateModality(@Valid @RequestBody ModalityRequestUpdate requestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return modalityService.updateModality(requestUpdate, httpServletRequest);
    }

    @PostMapping(ApiConstants.Modality.DELETE_PATH)
    @Operation(summary = "Delete modality", description = "Module -> Modality. Endpoint -> POST /modality/modality-delete/{id}")
    public ResponseMessage<BaseResult> deleteModality(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return modalityService.deleteModality(publicEntityKeyResolver.resolveFromPath(Entity.MODALITY, id, "Modality"), httpServletRequest);
    }
}


