package com.ut.emrPacs.controller;

import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.StudyListFilter;
import com.ut.emrPacs.service.service.StudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.UnknownHostException;

@RestController
@RequestMapping(ApiConstants.Study.BASE_PATH)
@Tag(
        name = "14. Study Controller",
        description = "Study workflow controller. Frontend should use study.status for study result state. Status set: IMAGE_RECEIVED, COMPLETED."
)
public class StudyController {

    @Autowired
    private StudyService studyService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.Study.LIST_PATH)
    @Operation(summary = "List studies", description = "Module -> Study. Endpoint -> POST /study/study-list")
    public ResponseMessage<BaseResult> list(@Valid @RequestBody StudyListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return studyService.list(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Study.FIND_PATH)
    @Operation(summary = "Find study by ID", description = "Module -> Study. Endpoint -> POST /study/study-find/{id}")
    public ResponseMessage<BaseResult> find(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return studyService.findById(publicEntityKeyResolver.resolveFromPath(Entity.STUDY, id, "Study"), httpServletRequest);
    }
}

