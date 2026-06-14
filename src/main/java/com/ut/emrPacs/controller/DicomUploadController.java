package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.pacs.dicomUpload.DicomUploadRequest;
import com.ut.emrPacs.service.service.DicomUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.UnknownHostException;
import java.util.List;

@RestController
@RequestMapping(ApiConstants.DicomUpload.BASE_PATH)
@Tag(name = "16. DICOM Upload Controller", description = "Upload DICOM files through the API, stream them to the hospital DICOM server, and persist metadata only.")
public class DicomUploadController {

    @Autowired
    private DicomUploadService dicomUploadService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload DICOM files",
            description = "Endpoint -> POST /dicom-uploads. Multipart parts: hospitalKey, repeated files, or one zipFile. DICOM binary is streamed to the DICOM server and not stored in the API database. referenceVisitCode is derived from the DICOM AccessionNumber tag."
    )
    public ResponseMessage<BaseResult> uploadDicom(
            @ModelAttribute DicomUploadRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart(value = "zipFile", required = false) MultipartFile zipFile,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return dicomUploadService.uploadDicom(request, files, zipFile, httpServletRequest);
    }
}
