package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.systemSettings.hospital.HospitalModalityRequest;
import com.ut.emrPacs.service.service.ModalityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.UnknownHostException;

@RestController
@Validated
@Tag(name = "5. Hospital Modality Controller", description = "Endpoints for hospital-modality relationships.")
public class HospitalModalityController {

    @Autowired
    private ModalityService modalityService;

    @PostMapping(ApiConstants.HospitalModality.LIST_BY_USER_PATH)
    @Operation(summary = "List hospitals with modalities by user", description = "Module -> Hospital Modality. Endpoint -> POST /hospital-modality")
    public ResponseMessage<BaseResult> listHospitalModality(@Valid @RequestBody(required = false) HospitalModalityRequest hospitalModalityRequest, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return modalityService.listHospitalModalityByUser(hospitalModalityRequest, httpServletRequest);
    }
}


