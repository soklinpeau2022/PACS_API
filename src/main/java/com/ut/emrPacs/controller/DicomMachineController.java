package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalDicomMachineListRequest;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalDicomMachineRequestUpdate;
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
@RequestMapping(ApiConstants.DicomMachine.BASE_PATH)
@Tag(name = "14. Dicom Machine Controller", description = "Endpoints for reusable hospital modality machines and rooms.")
public class DicomMachineController {

    @Autowired
    private DicomServerService dicomServerService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.DicomMachine.LIST_PATH)
    @Operation(summary = "List DICOM machines", description = "Module -> DICOM Machine. Endpoint -> POST /dicom-machine/dicom-machine-list")
    public ResponseMessage<BaseResult> list(@Valid @RequestBody HospitalDicomMachineListRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.listDicomMachines(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomMachine.FIND_PATH)
    @Operation(summary = "Find DICOM machine by ID", description = "Module -> DICOM Machine. Endpoint -> POST /dicom-machine/dicom-machine-find/{id}")
    public ResponseMessage<BaseResult> find(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.getDicomMachineById(publicEntityKeyResolver.resolveFromPath(Entity.DICOM_MACHINE, id, "DICOM machine"), httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomMachine.CREATE_PATH)
    @Operation(summary = "Create DICOM machine", description = "Create a reusable hospital modality machine. Endpoint -> POST /dicom-machine/dicom-machine-create")
    public ResponseMessage<BaseResult> create(@Valid @RequestBody HospitalDicomMachineRequestUpdate request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.createDicomMachine(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomMachine.UPDATE_PATH)
    @Operation(summary = "Update DICOM machine", description = "Update a reusable hospital modality machine. Endpoint -> POST /dicom-machine/dicom-machine-update")
    public ResponseMessage<BaseResult> update(@Valid @RequestBody HospitalDicomMachineRequestUpdate request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.updateDicomMachine(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomMachine.DELETE_PATH)
    @Operation(summary = "Delete DICOM machine", description = "Soft-delete a reusable hospital modality machine. Endpoint -> POST /dicom-machine/dicom-machine-delete/{id}")
    public ResponseMessage<BaseResult> delete(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dicomServerService.deleteDicomMachine(publicEntityKeyResolver.resolveFromPath(Entity.DICOM_MACHINE, id, "DICOM machine"), httpServletRequest);
    }
}
