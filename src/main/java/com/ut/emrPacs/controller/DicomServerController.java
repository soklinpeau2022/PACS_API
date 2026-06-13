package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.DicomServerFilter;
import com.ut.emrPacs.model.dto.request.pacs.dicom.DicomServerHealthSettingsRequest;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalDicomServerRequestUpdate;
import com.ut.emrPacs.service.service.DicomServerService;
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
@RequestMapping(ApiConstants.DicomServer.BASE_PATH)
@Tag(name = "13. Dicom Server Controller", description = "Endpoints for managing hospital DICOM servers.")
public class DicomServerController {

    @Autowired
    private DicomServerService dicomServerService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.DicomServer.LIST_PATH)
    @Operation(summary = "List DICOM servers", description = "Module -> DICOM Server. Endpoint -> POST /dicom-server/dicom-server-list")
    public ResponseMessage<BaseResult> list(@Valid @RequestBody DicomServerFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.listDicomServers(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomServer.HEALTH_LIST_PATH)
    @Operation(summary = "List DICOM server health", description = "Module -> DICOM Server. Endpoint -> POST /dicom-server/dicom-server-health-list")
    public ResponseMessage<BaseResult> healthList(@RequestBody(required = false) DicomServerFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.listDicomServerHealth(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomServer.HEALTH_SUMMARY_PATH)
    @Operation(summary = "DICOM server health summary", description = "Module -> DICOM Server. Endpoint -> POST /dicom-server/dicom-server-health-summary")
    public ResponseMessage<BaseResult> healthSummary(@RequestBody(required = false) DicomServerFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.getDicomServerHealthSummary(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomServer.HEALTH_SETTINGS_GET_PATH)
    @Operation(summary = "Get DICOM server health settings", description = "Module -> DICOM Server. Endpoint -> POST /dicom-server/dicom-server-health-settings-get")
    public ResponseMessage<BaseResult> healthSettingsGet(HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.getDicomServerHealthSettings(httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomServer.HEALTH_SETTINGS_UPDATE_PATH)
    @Operation(summary = "Update DICOM server health settings", description = "Module -> DICOM Server. Endpoint -> POST /dicom-server/dicom-server-health-settings-update")
    public ResponseMessage<BaseResult> healthSettingsUpdate(@RequestBody(required = false) DicomServerHealthSettingsRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.updateDicomServerHealthSettings(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomServer.FIND_PATH)
    @Operation(summary = "Find DICOM server by ID", description = "Module -> DICOM Server. Endpoint -> POST /dicom-server/dicom-server-find/{id}")
    public ResponseMessage<BaseResult> find(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.getDicomServerById(publicEntityKeyResolver.resolveFromPath(Entity.DICOM_SERVER, id, "DICOM server"), httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomServer.CREATE_PATH)
    @Operation(summary = "Create DICOM server", description = "Module -> DICOM Server. Endpoint -> POST /dicom-server/dicom-server-create")
    public ResponseMessage<BaseResult> create(@Valid @RequestBody HospitalDicomServerRequestUpdate request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.createDicomServer(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomServer.UPDATE_PATH)
    @Operation(summary = "Update DICOM server", description = "Module -> DICOM Server. Endpoint -> POST /dicom-server/dicom-server-update")
    public ResponseMessage<BaseResult> update(@Valid @RequestBody HospitalDicomServerRequestUpdate request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.updateDicomServer(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomServer.DELETE_PATH)
    @Operation(summary = "Delete DICOM server", description = "Module -> DICOM Server. Endpoint -> POST /dicom-server/dicom-server-delete/{id}")
    public ResponseMessage<BaseResult> delete(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.deleteDicomServer(publicEntityKeyResolver.resolveFromPath(Entity.DICOM_SERVER, id, "DICOM server"), httpServletRequest);
    }
}
