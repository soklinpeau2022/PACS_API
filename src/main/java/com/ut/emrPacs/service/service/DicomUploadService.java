package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.pacs.dicomUpload.DicomUploadRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.List;

public interface DicomUploadService {
    ResponseMessage<BaseResult> uploadDicom(
            DicomUploadRequest request,
            List<MultipartFile> files,
            MultipartFile zipFile,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException;

    /**
     * Process an already-reassembled DICOM ZIP file on disk (chunked-upload completion). Resolves the
     * hospital/DICOM server, validates size, streams entries to the DICOM server, returns the same
     * response shape as {@link #uploadDicom}. Runs with no surrounding DB transaction.
     */
    ResponseMessage<BaseResult> uploadDicomZipFile(
            String hospitalKey,
            String dicomServerKey,
            Path zipPath,
            long zipSize,
            HttpServletRequest httpServletRequest
    );

    /** Base temp directory for DICOM uploads (used to stage chunked-upload parts). */
    Path resolveUploadTempDir() throws IOException;
}
