package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.pacs.dicomUpload.DicomChunkUploadInitRequest;
import com.ut.emrPacs.service.service.DicomChunkUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(ApiConstants.DicomUpload.BASE_PATH)
@Tag(name = "16. DICOM Chunk Upload Controller", description = "Chunked ZIP upload transfer for large DICOM archives.")
public class DicomChunkUploadController {

    @Autowired
    private DicomChunkUploadService dicomChunkUploadService;

    @PostMapping(ApiConstants.DicomUpload.CHUNK_INIT_PATH)
    @Operation(summary = "Start a chunked DICOM ZIP upload")
    public ResponseMessage<BaseResult> init(
            @RequestBody DicomChunkUploadInitRequest request,
            HttpServletRequest httpServletRequest
    ) {
        if (UserAuthSession.getCurrentUser() == null) {
            return unauthorized();
        }
        return dicomChunkUploadService.init(request, httpServletRequest);
    }

    @PostMapping(value = ApiConstants.DicomUpload.CHUNK_UPLOAD_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload one DICOM ZIP chunk")
    public ResponseMessage<BaseResult> uploadChunk(
            @PathVariable String uploadId,
            @RequestParam("index") Integer index,
            @RequestPart("chunk") MultipartFile chunk,
            HttpServletRequest httpServletRequest
    ) {
        if (UserAuthSession.getCurrentUser() == null) {
            return unauthorized();
        }
        return dicomChunkUploadService.uploadChunk(uploadId, index, chunk, httpServletRequest);
    }

    @GetMapping(ApiConstants.DicomUpload.CHUNK_STATUS_PATH)
    @Operation(summary = "Get chunk transfer and DICOM server processing progress")
    public ResponseMessage<BaseResult> status(
            @PathVariable String uploadId,
            HttpServletRequest httpServletRequest
    ) {
        if (UserAuthSession.getCurrentUser() == null) {
            return unauthorized();
        }
        return dicomChunkUploadService.status(uploadId, httpServletRequest);
    }

    @PostMapping(ApiConstants.DicomUpload.CHUNK_COMPLETE_PATH)
    @Operation(summary = "Complete a chunked DICOM ZIP upload")
    public ResponseMessage<BaseResult> complete(
            @PathVariable String uploadId,
            HttpServletRequest httpServletRequest
    ) {
        if (UserAuthSession.getCurrentUser() == null) {
            return unauthorized();
        }
        return dicomChunkUploadService.complete(uploadId, httpServletRequest);
    }

    @DeleteMapping(ApiConstants.DicomUpload.CHUNK_ABORT_PATH)
    @Operation(summary = "Cancel a chunked DICOM ZIP upload")
    public ResponseMessage<BaseResult> abort(
            @PathVariable String uploadId,
            HttpServletRequest httpServletRequest
    ) {
        if (UserAuthSession.getCurrentUser() == null) {
            return unauthorized();
        }
        return dicomChunkUploadService.abort(uploadId, httpServletRequest);
    }

    private ResponseMessage<BaseResult> unauthorized() {
        return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
    }
}
