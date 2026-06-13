package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.PacsResultTemplateFilter;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultTemplateSaveRequest;
import com.ut.emrPacs.service.service.PacsResultTemplateService;
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
@RequestMapping(ApiConstants.PacsResultTemplate.BASE_PATH)
@Tag(name = "21. PACS Result Template Controller", description = "Doctor report template CRUD endpoints.")
public class PacsResultTemplateController {
    @Autowired
    private PacsResultTemplateService pacsResultTemplateService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.PacsResultTemplate.LIST_PATH)
    @Operation(summary = "List PACS result templates", description = "Module -> PACS Result Template. Endpoint -> POST /pacs-result-template/pacs-result-template-list")
    public ResponseMessage<BaseResult> list(@Valid @RequestBody PacsResultTemplateFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return pacsResultTemplateService.listTemplates(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultTemplate.FIND_PATH)
    @Operation(summary = "Find PACS result template", description = "Module -> PACS Result Template. Endpoint -> POST /pacs-result-template/pacs-result-template-find/{id}")
    public ResponseMessage<BaseResult> find(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return pacsResultTemplateService.findTemplate(publicEntityKeyResolver.resolveFromPath(Entity.PACS_RESULT_TEMPLATE, id, "Template"), httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultTemplate.CREATE_PATH)
    @Operation(summary = "Create PACS result template", description = "Module -> PACS Result Template. Endpoint -> POST /pacs-result-template/pacs-result-template-create")
    public ResponseMessage<BaseResult> create(@Valid @RequestBody PacsResultTemplateSaveRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return pacsResultTemplateService.createTemplate(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultTemplate.UPDATE_PATH)
    @Operation(summary = "Update PACS result template", description = "Module -> PACS Result Template. Endpoint -> POST /pacs-result-template/pacs-result-template-update")
    public ResponseMessage<BaseResult> update(@Valid @RequestBody PacsResultTemplateSaveRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return pacsResultTemplateService.updateTemplate(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultTemplate.DELETE_PATH)
    @Operation(summary = "Delete PACS result template", description = "Module -> PACS Result Template. Endpoint -> POST /pacs-result-template/pacs-result-template-delete/{id}")
    public ResponseMessage<BaseResult> delete(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return pacsResultTemplateService.deleteTemplate(publicEntityKeyResolver.resolveFromPath(Entity.PACS_RESULT_TEMPLATE, id, "Template"), httpServletRequest);
    }
}
