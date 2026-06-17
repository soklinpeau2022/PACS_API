package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.pacs.dicomUpload.DicomChunkUploadInitRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

public interface DicomChunkUploadService {
    ResponseMessage<BaseResult> init(DicomChunkUploadInitRequest request, HttpServletRequest httpServletRequest);

    ResponseMessage<BaseResult> uploadChunk(String uploadId, Integer index, MultipartFile chunk, HttpServletRequest httpServletRequest);

    ResponseMessage<BaseResult> complete(String uploadId, HttpServletRequest httpServletRequest);

    ResponseMessage<BaseResult> abort(String uploadId, HttpServletRequest httpServletRequest);
}
