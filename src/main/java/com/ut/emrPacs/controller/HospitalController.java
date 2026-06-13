package com.ut.emrPacs.controller;

import java.net.UnknownHostException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.HospitalListFilter;
import com.ut.emrPacs.model.dto.request.systemSettings.hospital.HospitalRequestUpdate;
import com.ut.emrPacs.service.service.HospitalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
@RestController
@Validated
@RequestMapping(ApiConstants.Hospital.BASE_PATH)
@Tag(name = "3. Hospital Controller", description = "Endpoints for managing hospitals")
public class HospitalController {

    @Autowired
    private HospitalService hospitalService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.Hospital.LIST_PATH)
    @Operation(
        summary = "Get Hospital List",
        description = "Retrieve a list of hospitals with optional filtering. Module -> Hospital. Endpoint -> POST /hospital/list. Frontend integration: Request details -> Request body Filter. Filter object: send only used fields; supports filtering and pagination fields (for example page and rowsPerPage) when available. Response format -> ResponseMessage<BaseResult> with success, message, and data; list endpoints also include pagination metadata when applicable. Security -> use Bearer token on protected endpoints."
    )
    public ResponseMessage<BaseResult> listHospital(@Valid @RequestBody HospitalListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (!hasAuthenticatedContext()) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return hospitalService.listHospital(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Hospital.FIND_PATH)
    @Operation(
        summary = "Find Hospital by ID",
        description = "Retrieve hospital details by hospital ID. Module -> Hospital. Endpoint -> POST /hospital/find/{id}. Frontend integration: Request details -> Path variable 'id' (Long). Response format -> ResponseMessage<BaseResult> with success, message, and data; list endpoints also include pagination metadata when applicable. Security -> use Bearer token on protected endpoints."
    )
    public ResponseMessage<BaseResult> getHospitalById(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (!hasAuthenticatedContext()) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return hospitalService.getHospitalById(publicEntityKeyResolver.resolveFromPath(Entity.HOSPITAL, id, "Hospital"), httpServletRequest);
    }

    @PostMapping(value = ApiConstants.Hospital.UPDATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Update Hospital",
        description = "Update hospital details by ID. Module -> Hospital. Endpoint -> POST /hospital/hospital-update. " +
                "Request body supports hospital profile fields such as code, abbreviation, timezone, users, and modalities. " +
                "hospitalUserList preferred format is numeric user IDs (example: [1,2,3]); legacy format [{\"userId\":1}] is also accepted."
    )
    public ResponseMessage<BaseResult> updateHospital(@Valid @RequestBody HospitalRequestUpdate requestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (!hasAuthenticatedContext()) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return hospitalService.updateHospital(requestUpdate, httpServletRequest);
    }

    @PostMapping(value = ApiConstants.Hospital.UPDATE_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Update Hospital with logo",
        description = "Update hospital details and optionally replace the hospital logo. Module -> Hospital. Endpoint -> POST /hospital/hospital-update."
    )
    public ResponseMessage<BaseResult> updateHospitalMultipart(
            @Valid @ModelAttribute HospitalRequestUpdate requestUpdate,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        if (!hasAuthenticatedContext()) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return hospitalService.updateHospital(requestUpdate, logo, httpServletRequest);
    }

    @PostMapping(value = ApiConstants.Hospital.CREATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Create Hospital",
        description = "Create hospital details. Module -> Hospital. Endpoint -> POST /hospital/hospital-create. " +
                "Hospital create stores hospital profile, user assignment, and modality assignment only. " +
                "hospitalUserList preferred format is numeric user IDs (example: [1,2,3]); legacy format [{\"userId\":1}] is also accepted."
    )
    public ResponseMessage<BaseResult> createHospital(@Valid @RequestBody HospitalRequestUpdate requestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (!hasAuthenticatedContext()) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return hospitalService.createHospital(requestUpdate, httpServletRequest);
    }

    @PostMapping(value = ApiConstants.Hospital.CREATE_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Create Hospital with logo",
        description = "Create hospital details and optionally store a hospital logo. Module -> Hospital. Endpoint -> POST /hospital/hospital-create."
    )
    public ResponseMessage<BaseResult> createHospitalMultipart(
            @Valid @ModelAttribute HospitalRequestUpdate requestUpdate,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        if (!hasAuthenticatedContext()) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return hospitalService.createHospital(requestUpdate, logo, httpServletRequest);
    }

    private boolean hasAuthenticatedContext() {
        if (UserAuthSession.getCurrentUser() != null) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}


