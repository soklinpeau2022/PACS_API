package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.ModuleTypeFilter;
import com.ut.emrPacs.service.service.ModuleTypeService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.UnknownHostException;

@RestController
@Validated
@Tag(name = "6. Module Type Controller", description = "Endpoints for managing module types.")
@Timed
public class ModuleTypeController {

    @Autowired
    private ModuleTypeService moduleTypeService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.ModuleType.LIST_PATH)
    @Operation(
            summary = "List module types",
            description = "List module types. Module -> Module Type. Endpoint -> POST /module-type/module-type-list."
    )
    public ResponseMessage<BaseResult> listModuleType(@Valid @RequestBody ModuleTypeFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return moduleTypeService.listModuleType(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.ModuleType.BASE_PATH + ApiConstants.ModuleType.FIND_PATH)
    @Operation(
            summary = "Find module type by public key",
            description = "Find a module type by public key. Module -> Module Type. Endpoint -> POST /module-type/find/{key}."
    )
    public ResponseMessage<BaseResult> getModuleTypeById(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return moduleTypeService.getModuleTypeById(publicEntityKeyResolver.resolveFromPath(Entity.MODULE_TYPE, id, "Module type"), httpServletRequest);
    }
}


