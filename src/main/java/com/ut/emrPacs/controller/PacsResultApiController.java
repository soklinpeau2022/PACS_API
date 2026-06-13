package com.ut.emrPacs.controller;

import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.PublicViewerAuthorizeRequest;
import com.ut.emrPacs.service.service.PacsResultService;
import com.ut.emrPacs.service.service.WorklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.UnknownHostException;

@RestController
@RequestMapping(ApiConstants.PacsResultApi.BASE_PATH)
@Tag(
        name = "20. PACS Result Viewer State API",
        description = "Browser-scoped PACS-OHIF viewer state endpoints secured by a signed viewer access token."
)
public class PacsResultApiController {

    private final PacsResultService pacsResultService;
    private final WorklistService worklistService;

    public PacsResultApiController(PacsResultService pacsResultService, WorklistService worklistService) {
        this.pacsResultService = pacsResultService;
        this.worklistService = worklistService;
    }

    @PostMapping(ApiConstants.PacsResultApi.PUBLIC_VIEWER_AUTHORIZE_PATH)
    @Operation(
            summary = "Authorize patient result viewer",
            description = "Verifies a patient phone number against a scoped hospital/worklist link and returns a short-lived read-only viewer session."
    )
    public ResponseMessage<BaseResult> authorizePublicViewer(
            @Valid @RequestBody PublicViewerAuthorizeRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return worklistService.authorizePublicViewer(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultApi.VIEWER_STATE_FIND_PATH)
    @Operation(
            summary = "Find saved viewer state",
            description = "Module -> PACS Result Viewer State API. Restores measurements, annotations, segmentations, findings, and presentation state for the signed viewer scope."
    )
    public ResponseMessage<BaseResult> findViewerState(
            @Valid @RequestBody PacsViewerStateRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.findBrowserViewerState(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultApi.VIEWER_STATE_SAVE_PATH)
    @Operation(
            summary = "Save viewer state",
            description = "Module -> PACS Result Viewer State API. Creates or updates the latest viewer state for the signed study/worklist scope."
    )
    public ResponseMessage<BaseResult> saveViewerState(
            @Valid @RequestBody PacsViewerStateRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.saveBrowserViewerState(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultApi.VIEWER_STATE_DELETE_PATH)
    @Operation(
            summary = "Delete saved viewer state",
            description = "Module -> PACS Result Viewer State API. Soft-deletes viewer state for the signed study/worklist scope."
    )
    public ResponseMessage<BaseResult> deleteViewerState(
            @Valid @RequestBody PacsViewerStateRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.deleteBrowserViewerState(request, httpServletRequest);
    }
}
