package com.ut.emrPacs.controller;

import com.ut.emrPacs.config.ApiConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.Security.BASE_PATH)
public class SecurityReportController {

    @RequestMapping(
            value = ApiConstants.Security.CSP_REPORT_PATH,
            method = RequestMethod.POST,
            consumes = MediaType.ALL_VALUE
    )
    public ResponseEntity<Void> receiveCspReport(HttpServletRequest ignoredRequest) {
        return ResponseEntity.noContent().build();
    }
}
