package com.ut.emrPacs.controller;

import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultContextRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultFindByStudyRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultFindByWorklistRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultImageRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultImageUploadRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultSaveRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultTemplateListRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateChunkCompleteRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateChunkRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.PublicViewerAuthorizeRequest;
import com.ut.emrPacs.service.service.PacsResultService;
import com.ut.emrPacs.service.service.WorklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.UnknownHostException;
import java.util.List;

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

    @PostMapping(value = ApiConstants.PacsResultApi.CREATE_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Create or reuse PACS result from browser viewer",
            description = "Module -> PACS Result Viewer API. Creates or reuses a report for the signed viewer scope using viewer-token auth."
    )
    public ResponseMessage<BaseResult> createResult(
            @Valid @ModelAttribute PacsResultSaveRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.createBrowser(request, images, httpServletRequest);
    }

    @PostMapping(value = ApiConstants.PacsResultApi.CREATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create or reuse PACS result from browser viewer",
            description = "Module -> PACS Result Viewer API. Creates or reuses a report for the signed viewer scope using viewer-token auth."
    )
    public ResponseMessage<BaseResult> createResultJson(
            @Valid @RequestBody PacsResultSaveRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.createBrowser(request, List.of(), httpServletRequest);
    }

    @PostMapping(value = ApiConstants.PacsResultApi.UPDATE_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Update PACS result from browser viewer",
            description = "Module -> PACS Result Viewer API. Updates a report for the signed viewer scope using viewer-token auth."
    )
    public ResponseMessage<BaseResult> updateResult(
            @Valid @ModelAttribute PacsResultSaveRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.updateBrowser(request, images, httpServletRequest);
    }

    @PostMapping(value = ApiConstants.PacsResultApi.UPDATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update PACS result from browser viewer",
            description = "Module -> PACS Result Viewer API. Updates a report for the signed viewer scope using viewer-token auth."
    )
    public ResponseMessage<BaseResult> updateResultJson(
            @Valid @RequestBody PacsResultSaveRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.updateBrowser(request, List.of(), httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultApi.FIND_BY_STUDY_PATH)
    @Operation(summary = "Find PACS result by study from browser viewer")
    public ResponseMessage<BaseResult> findResultByStudy(
            @Valid @RequestBody PacsResultFindByStudyRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.findBrowserByStudy(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultApi.FIND_BY_WORKLIST_PATH)
    @Operation(summary = "Find PACS result by worklist from browser viewer")
    public ResponseMessage<BaseResult> findResultByWorklist(
            @Valid @RequestBody PacsResultFindByWorklistRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.findBrowserByWorklist(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultApi.CONTEXT_PATH)
    @Operation(summary = "Resolve PACS result context from browser viewer")
    public ResponseMessage<BaseResult> resultContext(
            @Valid @RequestBody PacsResultContextRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.getBrowserContext(request, httpServletRequest);
    }

    @PostMapping(value = ApiConstants.PacsResultApi.IMAGE_UPLOAD_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload PACS result images from browser viewer")
    public ResponseMessage<BaseResult> uploadResultImages(
            @Valid @ModelAttribute PacsResultImageUploadRequest request,
            @RequestPart("images") List<MultipartFile> images,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.uploadBrowserImages(request, images, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultApi.IMAGE_DELETE_PATH)
    @Operation(summary = "Delete PACS result image from browser viewer")
    public ResponseMessage<BaseResult> deleteResultImage(
            @Valid @RequestBody PacsResultImageRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.deleteBrowserImage(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultApi.IMAGE_CONTENT_PATH)
    @Operation(summary = "Read PACS result image from browser viewer")
    public ResponseEntity<Resource> readResultImage(
            @Valid @RequestBody PacsResultImageRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return pacsResultService.readBrowserImage(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultApi.HOSPITAL_LOGO_CONTENT_PATH)
    @Operation(summary = "Read PACS result hospital logo from browser viewer")
    public ResponseEntity<Resource> readResultHospitalLogo(
            @Valid @RequestBody PacsResultContextRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return pacsResultService.readBrowserHospitalLogo(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultApi.PUBLIC_VIEWER_HOSPITAL_LOGO_PATH)
    @Operation(
            summary = "Read hospital logo for the public patient result gate",
            description = "Public, pre-authentication endpoint: returns the hospital branding logo for a scoped public viewer link (hospital + worklist/study keys). Returns no PHI; rate-limited."
    )
    public ResponseEntity<Resource> readPublicViewerHospitalLogo(
            @Valid @RequestBody PacsResultContextRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return pacsResultService.readPublicViewerHospitalLogo(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultApi.TEMPLATE_LIST_PATH)
    @Operation(summary = "List PACS result templates from browser viewer")
    public ResponseMessage<BaseResult> listResultTemplates(
            @Valid @RequestBody PacsResultTemplateListRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.listBrowserTemplates(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultApi.TEMPLATE_FIND_PATH)
    @Operation(summary = "Find PACS result template from browser viewer")
    public ResponseMessage<BaseResult> findResultTemplate(
            @PathVariable String templateKey,
            @Valid @RequestBody PacsResultTemplateListRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.findBrowserTemplate(templateKey, request, httpServletRequest);
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

    @PostMapping(ApiConstants.PacsResultApi.VIEWER_STATE_SAVE_CHUNK_PATH)
    @Operation(
            summary = "Upload viewer state save chunk",
            description = "Module -> PACS Result Viewer State API. Accepts one base64 chunk for a large browser viewer-state save."
    )
    public ResponseMessage<BaseResult> saveViewerStateChunk(
            @Valid @RequestBody PacsViewerStateChunkRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.saveBrowserViewerStateChunk(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResultApi.VIEWER_STATE_SAVE_CHUNK_COMPLETE_PATH)
    @Operation(
            summary = "Complete viewer state chunked save",
            description = "Module -> PACS Result Viewer State API. Reassembles uploaded chunks and persists the viewer state through the normal save validator."
    )
    public ResponseMessage<BaseResult> completeViewerStateChunk(
            @Valid @RequestBody PacsViewerStateChunkCompleteRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.completeBrowserViewerStateChunk(request, httpServletRequest);
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
