package com.ut.emrPacs.controller;

import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.PatientListFilter;
import com.ut.emrPacs.model.dto.request.pacs.patient.PatientCreateRequest;
import com.ut.emrPacs.model.dto.request.pacs.patient.PatientUpdateRequest;
import com.ut.emrPacs.service.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.UnknownHostException;

@RestController
@RequestMapping(ApiConstants.Patient.BASE_PATH)
@Tag(name = "9. Patient Controller", description = "Endpoints for managing patients.")
public class PatientController {

    @Autowired
    private PatientService patientService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.Patient.LIST_PATH)
    @Operation(summary = "List patients", description = "Module -> Patient. Endpoint -> POST /patient/patient-list")
    public ResponseMessage<BaseResult> list(@Valid @RequestBody PatientListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return patientService.list(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Patient.FIND_PATH)
    @Operation(summary = "Find patient by ID", description = "Module -> Patient. Endpoint -> POST /patient/patient-find/{id}")
    public ResponseMessage<BaseResult> find(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return patientService.findById(publicEntityKeyResolver.resolveFromPath(Entity.PATIENT, id, "Patient"), httpServletRequest);
    }

    @PostMapping(ApiConstants.Patient.CREATE_PATH)
    @Operation(summary = "Create patient", description = "Module -> Patient. Endpoint -> POST /patient/patient-create")
    public ResponseMessage<BaseResult> create(@Valid @RequestBody PatientCreateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return patientService.create(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.Patient.UPDATE_PATH)
    @Operation(summary = "Update patient", description = "Module -> Patient. Endpoint -> POST /patient/patient-update")
    public ResponseMessage<BaseResult> update(@Valid @RequestBody PatientUpdateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return patientService.update(request, httpServletRequest);
    }
}

