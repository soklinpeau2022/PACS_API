package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalModalityServerRouteListRequest;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalModalityServerRouteRequestUpdate;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalModalityServerRouteSaveRequest;
import com.ut.emrPacs.service.service.DicomServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.UnknownHostException;

@RestController
@Validated
@RequestMapping(ApiConstants.DicomRouting.BASE_PATH)
@Tag(name = "15. Dicom Routing Controller", description = "Endpoints for linking saved machine rooms to DICOM servers.")
public class DicomRoutingController {

    @Autowired
    private DicomServerService dicomServerService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.DicomRouting.LIST_PATH)
    @Operation(summary = "List DICOM routes", description = "Module -> DICOM Routing. Endpoint -> POST /dicom-routing/dicom-routing-list")
    public ResponseMessage<BaseResult> list(@Valid @RequestBody HospitalModalityServerRouteListRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.listRouting(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomRouting.FIND_PATH)
    @Operation(summary = "Find DICOM route by ID", description = "Module -> DICOM Routing. Endpoint -> POST /dicom-routing/dicom-routing-find/{id}")
    public ResponseMessage<BaseResult> find(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.getRoutingById(publicEntityKeyResolver.resolveFromPath(Entity.DICOM_ROUTING_CONFIG, id, "DICOM route"), httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomRouting.CREATE_PATH)
    @Operation(summary = "Create DICOM route", description = "Add one or more routes. Module -> DICOM Routing. Endpoint -> POST /dicom-routing/dicom-routing-create")
    public ResponseMessage<BaseResult> create(@Valid @RequestBody HospitalModalityServerRouteSaveRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.createRouting(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomRouting.UPDATE_PATH)
    @Operation(summary = "Update DICOM route", description = "Module -> DICOM Routing. Endpoint -> POST /dicom-routing/dicom-routing-update")
    public ResponseMessage<BaseResult> update(@Valid @RequestBody HospitalModalityServerRouteRequestUpdate request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.updateRouting(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomRouting.DELETE_PATH)
    @Operation(summary = "Delete DICOM route", description = "Module -> DICOM Routing. Endpoint -> POST /dicom-routing/dicom-routing-delete/{id}")
    public ResponseMessage<BaseResult> delete(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.deleteRouting(publicEntityKeyResolver.resolveFromPath(Entity.DICOM_ROUTING_CONFIG, id, "DICOM route"), httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomRouting.BUILD_CONFIG_PATH)
    @Operation(summary = "Build DicomServer project zip", description = "Generate ready DicomServer deployment package from DICOM Server and DICOM Routing. Endpoint -> POST /dicom-routing/dicom-routing-build-config/{id}")
    public ResponseMessage<BaseResult> buildConfig(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.buildRoutingDicomServerConfig(publicEntityKeyResolver.resolveFromPath(Entity.DICOM_ROUTING_CONFIG, id, "DICOM route"), httpServletRequest);
    }

    @GetMapping(ApiConstants.DicomRouting.BUILD_CONFIG_DOWNLOAD_PATH)
    @Operation(summary = "Download DicomServer project zip", description = "Stream the ready DicomServer deployment package with the offline Docker image inside the zip. Endpoint -> GET /dicom-routing/dicom-routing-build-config-download/{id}")
    public ResponseEntity<StreamingResponseBody> downloadConfigZip(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseEntity.status(401).build();
        }
        return dicomServerService.downloadRoutingDicomServerConfigZip(publicEntityKeyResolver.resolveFromPath(Entity.DICOM_ROUTING_CONFIG, id, "DICOM route"), httpServletRequest);
    }

    @GetMapping(ApiConstants.DicomRouting.BASE_IMAGE_DOWNLOAD_PATH)
    @Operation(summary = "Download DicomServer offline base image", description = "Download the API-server cached dicom_server_base Docker image archive. Endpoint -> GET /dicom-routing/dicom-routing-base-image-download")
    public ResponseEntity<Resource> downloadBaseImage(HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseEntity.status(401).build();
        }
        return dicomServerService.downloadDicomServerBaseImage(httpServletRequest);
    }
}
