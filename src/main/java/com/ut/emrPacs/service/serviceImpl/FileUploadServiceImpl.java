package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.helper.FileUploadHelper;
import com.ut.emrPacs.helper.FunctionHelper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.FileUploadService;
import com.ut.emrPacs.service.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.UnknownHostException;
import java.time.LocalTime;

@Service
public class FileUploadServiceImpl implements FileUploadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadServiceImpl.class);

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ActivityLogService activityLogService;

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> createFileUpload(MultipartFile file, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Insert flow: validate request, create file upload, and return operation result.
        LocalTime startDuration = LocalTime.now();
        String filePhoto = "";
        long line = 1000;

        try {
            Long userId = userService.getUserAuth().getId();

            if (file == null || file.isEmpty()) {
                LOGGER.warn("No file provided or file is empty.");
                return ResponseMessageUtils.makeResponse(false, messageService.message("No file provided or file is empty.", false));
            }

            // Save file uploaded
            filePhoto = FileUploadHelper.saveFileUploaded(file);
            if (filePhoto.isEmpty()) {
                LOGGER.warn("File upload failed: Unable to save the file.");
                return ResponseMessageUtils.makeResponse(false, messageService.message("File upload failed.", false));
            }

            LOGGER.info("File successfully uploaded to path: {}", filePhoto);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    "/file/insert", null, null, "Upload file", "Upload file", "Upload file", 1, "File uploaded successfully", startDuration, endDuration, httpServletRequest
            );

            return ResponseMessageUtils.makeResponse(true, messageService.message(filePhoto, true));

        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    "/file/insert", line, error.toString(), "Upload file", "Upload file", "Upload file", 2, "Error during file upload", startDuration, endDuration, httpServletRequest
            );

            return ResponseMessageUtils.makeResponse(false, messageService.message("Error during file upload", null, false));
        }
    }

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> deleteFileUpload(String storedPath, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Delete flow: validate request, remove or deactivate file upload, and return operation result.
        final LocalTime startDuration = LocalTime.now();
        Long line = 1033L;
        try {
            final Long userId = userService.getUserAuth().getId();

            if (!FunctionHelper.hasText(storedPath)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("File path is required.", false));
            }

            final String filename = FileUploadHelper.extractFilename(storedPath);
            if (filename.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid file path.", false));
            }

            // Delete uploaded file
            final boolean deleted = FileUploadHelper.deleteUploadedFile(filename);
            final LocalTime endDuration = LocalTime.now();

            if (deleted) {
                activityLogService.insert(
                        "/file/delete", null, null,
                        "Upload file", "Upload file", "Delete file",
                        1, "File deleted successfully",
                        startDuration, endDuration, httpServletRequest
                );
                return ResponseMessageUtils.makeResponse(true, messageService.message("File deleted.", true));
            } else {
                activityLogService.insert(
                        "/file/delete", null, null,
                        "Upload file", "Upload file", "Delete file",
                        2, "File not found or cannot be deleted",
                        startDuration, endDuration, httpServletRequest
                );
                return ResponseMessageUtils.makeResponse(false, messageService.message("File not found or cannot be deleted.", false));
            }
        } catch (Exception e) {
            final LocalTime endDuration = LocalTime.now();
            LOGGER.error("Error during file deletion: {}", e.toString(), e);
            activityLogService.insert(
                    "/file/delete", line, e.toString(),
                    "Upload file", "Upload file", "Delete file",
                    2, "Error during file deletion",
                    startDuration, endDuration, httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("Error", false));
        }
    }



}

