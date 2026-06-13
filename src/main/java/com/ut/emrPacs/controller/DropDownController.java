package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.DropDownFilter;
import com.ut.emrPacs.service.service.DropDownService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(ApiConstants.Dropdown.BASE_PATH)
@Tag(name = "2. Dropdown Controller", description = "Endpoints for dropdown data sources.")
@Timed
public class DropDownController {

    @Autowired
    private DropDownService dropDownService;

    @PostMapping(ApiConstants.Dropdown.NATIONALITY_PATH)
    @Operation(
        summary = "Get nationality dropdown",
        description = "Retrieve the nationality dropdown list. Module -> Dropdown. Endpoint -> POST /dropdown/dropdown-nationality."
    )
    public ResponseMessage<BaseResult> getNationality(@Valid @RequestBody DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dropDownService.getListCountries(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Dropdown.HOSPITAL_PATH)
    @Operation(
        summary = "Get hospital dropdown",
        description = "Retrieve the hospital dropdown list mapped to the logged-in user. Module -> Dropdown. Endpoint -> POST /dropdown/dropdown-hospital."
    )
    public ResponseMessage<BaseResult> getHospital(@Valid @RequestBody DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dropDownService.getListHospitalsByUser(filter, httpServletRequest);
    }
    @PostMapping(ApiConstants.Dropdown.MODALITY_PATH)
    @Operation(
        summary = "Get modality dropdown",
        description = "Retrieve the modality dropdown list for the logged-in user's hospital. Returns only value=id and label=name. Module -> Dropdown. Endpoint -> POST /dropdown/dropdown-modality."
    )
    public ResponseMessage<BaseResult> getModality(@Valid @RequestBody DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dropDownService.getListModalities(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Dropdown.MODALITY_CATALOG_PATH)
    @Operation(
        summary = "Get modality catalog dropdown",
        description = "Retrieve all active modality catalog rows for setup screens such as Hospital create/edit. This endpoint does not require an existing hospital_modality relation. Endpoint -> POST /dropdown/dropdown-modality-catalog."
    )
    public ResponseMessage<BaseResult> getModalityCatalog(@Valid @RequestBody DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dropDownService.getListModalityCatalog(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Dropdown.DICOM_SERVER_PATH)
    @Operation(
        summary = "Get DICOM server dropdown",
        description = "Retrieve DICOM server dropdown rows for a hospital. Returns value/label plus DICOM server URL, Viewer URL, AE title, username, and active status. Password is never returned. Module -> Dropdown. Endpoint -> POST /dropdown/dropdown-dicom-server."
    )
    public ResponseMessage<BaseResult> getDicomServer(@Valid @RequestBody DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dropDownService.getListDicomServers(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Dropdown.USER_GROUP_MEMBER_PATH)
    @Operation(
        summary = "Get user group member dropdown",
        description = "Retrieve the user group member dropdown list for hospital-based group assignment. Endpoint -> POST /dropdown/dropdown-user-group-member. Non-super-admin users are restricted to their login hospital."
    )
    public ResponseMessage<BaseResult> getUserGroupMembers(@Valid @RequestBody DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dropDownService.getListUserGroupMembers(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Dropdown.USER_PATH)
    @Operation(
        summary = "Get user dropdown",
        description = "Retrieve the user dropdown list with hospital-scope security. Endpoint -> POST /dropdown/dropdown-user."
    )
    public ResponseMessage<BaseResult> getUsers(@Valid @RequestBody DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dropDownService.getListUsers(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Dropdown.PATIENT_PATH)
    @Operation(
        summary = "Get patient dropdown",
        description = "Retrieve the patient dropdown list with hospital-scope security. Endpoint -> POST /dropdown/dropdown-patient."
    )
    public ResponseMessage<BaseResult> getPatients(@Valid @RequestBody DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dropDownService.getListPatients(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Dropdown.USER_GROUP_PATH)
    @Operation(
        summary = "Get user group dropdown",
        description = "Retrieve the user group dropdown list with hospital-scope security. Endpoint -> POST /dropdown/dropdown-user-group."
    )
    public ResponseMessage<BaseResult> getUserGroups(@Valid @RequestBody DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dropDownService.getListUserGroups(filter, httpServletRequest);
    }
}


