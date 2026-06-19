package com.ut.emrPacs.controller;

import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultFindByWorklistRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultFindByStudyRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultContextRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultImageUploadRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultImageRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultSaveRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultTemplateListRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateRequest;
import com.ut.emrPacs.service.service.PacsResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.UnknownHostException;
import java.util.List;

@RestController
@RequestMapping(ApiConstants.PacsResult.BASE_PATH)
@Tag(name = "20. PACS Result Controller", description = "Static-auth result input endpoints for PACS-OHIF Viewer.")
public class PacsResultController {

    @Autowired
    private PacsResultService pacsResultService;

    @PostMapping(value = ApiConstants.PacsResult.CREATE_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create or reuse PACS result", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-create")
    public ResponseMessage<BaseResult> create(
            @Valid @ModelAttribute PacsResultSaveRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.create(request, images, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResult.FIND_BY_STUDY_PATH)
    @Operation(summary = "Find PACS result by study", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-find-by-study")
    public ResponseMessage<BaseResult> findByStudy(
            @Valid @RequestBody PacsResultFindByStudyRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.findByStudy(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResult.FIND_BY_WORKLIST_PATH)
    @Operation(summary = "Find PACS result by Worklist", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-find-by-worklist")
    public ResponseMessage<BaseResult> findByWorklist(
            @Valid @RequestBody PacsResultFindByWorklistRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.findByWorklist(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResult.CONTEXT_PATH)
    @Operation(summary = "Resolve PACS result context", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-context. Used by PACS-OHIF Input Result to recover hospital, modality, Worklist, study, and patient identifiers.")
    public ResponseMessage<BaseResult> context(
            @Valid @RequestBody PacsResultContextRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.getContext(request, httpServletRequest);
    }

    @PostMapping(value = ApiConstants.PacsResult.UPDATE_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update PACS result", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-update")
    public ResponseMessage<BaseResult> update(
            @Valid @ModelAttribute PacsResultSaveRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.update(request, images, httpServletRequest);
    }

    @PostMapping(value = ApiConstants.PacsResult.IMAGE_UPLOAD_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload PACS result images", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-image-upload")
    public ResponseMessage<BaseResult> uploadImages(
            @Valid @ModelAttribute PacsResultImageUploadRequest request,
            @RequestPart("images") List<MultipartFile> images,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.uploadImages(request, images, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResult.IMAGE_DELETE_PATH)
    @Operation(summary = "Delete PACS result image", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-image-delete")
    public ResponseMessage<BaseResult> deleteImage(
            @Valid @RequestBody PacsResultImageRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.deleteImage(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResult.IMAGE_CONTENT_PATH)
    @Operation(summary = "Read PACS result image", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-image-content")
    public ResponseEntity<Resource> readImage(
            @Valid @RequestBody PacsResultImageRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return pacsResultService.readImage(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResult.HOSPITAL_LOGO_CONTENT_PATH)
    @Operation(summary = "Read hospital logo for PACS result print", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-hospital-logo-content")
    public ResponseEntity<Resource> readHospitalLogo(
            @Valid @RequestBody PacsResultContextRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return pacsResultService.readHospitalLogo(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResult.TEMPLATE_LIST_PATH)
    @Operation(summary = "List PACS result templates for viewer", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-template-list. Used by PACS-OHIF Viewer to load template dropdown options by hospital and modality without loading template content.")
    public ResponseMessage<BaseResult> listTemplates(
            @Valid @RequestBody PacsResultTemplateListRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.listTemplates(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResult.TEMPLATE_FIND_PATH)
    @Operation(summary = "Find PACS result template for viewer", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-template-find/{templateKey}. Used by PACS-OHIF Viewer to load rich template content only after a doctor selects a template.")
    public ResponseMessage<BaseResult> findTemplate(
            @PathVariable String templateKey,
            @Valid @RequestBody PacsResultTemplateListRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.findTemplate(templateKey, request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResult.VIEWER_STATE_FIND_PATH)
    @Operation(summary = "Find saved viewer state", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-viewer-state-find. Used by PACS-OHIF Viewer to restore measurements, annotations, segmentations, and finding state for a study.")
    public ResponseMessage<BaseResult> findViewerState(
            @Valid @RequestBody PacsViewerStateRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.findViewerState(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResult.VIEWER_STATE_META_PATH)
    @Operation(summary = "Find saved viewer state metadata", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-viewer-state-meta. Returns scope and payload size of a saved PACS-OHIF Viewer state WITHOUT the heavy JSONB payload (existence/size/freshness check).")
    public ResponseMessage<BaseResult> findViewerStateMeta(
            @Valid @RequestBody PacsViewerStateRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.findViewerStateMeta(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResult.VIEWER_STATE_SAVE_PATH)
    @Operation(summary = "Save viewer state", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-viewer-state-save. Stores the latest PACS-OHIF Viewer state for a scoped study/worklist.")
    public ResponseMessage<BaseResult> saveViewerState(
            @Valid @RequestBody PacsViewerStateRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.saveViewerState(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.PacsResult.VIEWER_STATE_DELETE_PATH)
    @Operation(summary = "Delete saved viewer state", description = "Module -> PACS Result. Endpoint -> POST /pacs-result/pacs-result-viewer-state-delete. Soft-deletes persisted PACS-OHIF Viewer state for a study.")
    public ResponseMessage<BaseResult> deleteViewerState(
            @Valid @RequestBody PacsViewerStateRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return pacsResultService.deleteViewerState(request, httpServletRequest);
    }
}
