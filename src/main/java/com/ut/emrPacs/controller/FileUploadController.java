package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.FileUploadHelper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.service.service.FileUploadService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.UnknownHostException;

import org.springframework.validation.annotation.Validated;
@RestController
@Validated
@RequestMapping(ApiConstants.FileUpload.BASE_PATH)
@Tag(name = "15. Upload File Controller", description = "Endpoints for file upload, retrieval, and deletion.")
@Timed
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    @PostMapping(value = ApiConstants.FileUpload.UPLOAD_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload file",
        description = "Upload a file. Module -> File Upload. Endpoint -> POST /file/file-upload. Frontend integration: Request details -> multipart form-data part 'file' (MultipartFile). Response format -> ResponseMessage<BaseResult> with success, message, and data. Security -> use a Bearer token on protected endpoints."
    )
    public ResponseMessage<BaseResult> uploadImage(@RequestPart(name = "file", required = false) MultipartFile file, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return fileUploadService.createFileUpload(file, httpServletRequest);
    }

    @GetMapping(ApiConstants.FileUpload.UPLOAD_CONTENT_PATH)
    @Operation(
        summary = "Get uploaded file",
        description = "Read and stream an uploaded file by filename from storage. Module -> File Upload. Endpoint -> GET /file/file-upload/{filename:.+}. Frontend integration: Request details -> path variable 'filename' (String). Security -> use a Bearer token on protected endpoints."
    )
    public ResponseEntity<Resource> readFile(@PathVariable @NotBlank @Size(max = 255) String filename) {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String safeFilename = FileUploadHelper.extractFilename("upload/" + filename);
        if (safeFilename.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        File file = new File(System.getProperty("catalina.base", System.getProperty("java.io.tmpdir"))
                + "/logs/EMRLogs/upload/" + safeFilename);

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        MediaType mediaType = MediaTypeFactory.getMediaType(file.getName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        String disposition = "attachment";
        if ("image".equalsIgnoreCase(mediaType.getType()) && !"svg+xml".equalsIgnoreCase(mediaType.getSubtype())) {
            disposition = "inline";
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + file.getName() + "\"")
                .body(resource);
    }
    @DeleteMapping(ApiConstants.FileUpload.DELETE_PATH)
    @Operation(
        summary = "Delete uploaded file",
        description = "Delete a previously uploaded file using its stored path, for example /upload/xxxx.jpg. Module -> File Upload. Endpoint -> DELETE /file/file-delete. Frontend integration: Request details -> query parameter 'path' (String). Response format -> ResponseMessage<BaseResult> with success, message, and data. Security -> use a Bearer token on protected endpoints."
    )
    public ResponseMessage<BaseResult> deleteUploadedFile(
            @RequestParam("path") @NotBlank @Size(max = 1024) String path,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return fileUploadService.deleteFileUpload(path, httpServletRequest);
    }

}


