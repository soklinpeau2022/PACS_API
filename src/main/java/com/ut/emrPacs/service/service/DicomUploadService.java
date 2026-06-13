package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.pacs.dicomUpload.DicomUploadRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.net.UnknownHostException;
import java.util.List;

public interface DicomUploadService {
    ResponseMessage<BaseResult> uploadDicom(
            DicomUploadRequest request,
            List<MultipartFile> files,
            MultipartFile zipFile,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException;
}
