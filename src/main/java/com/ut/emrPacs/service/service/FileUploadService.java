package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.net.UnknownHostException;

public interface FileUploadService {

    ResponseMessage<BaseResult> createFileUpload(MultipartFile file, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deleteFileUpload(String storedPath, HttpServletRequest httpServletRequest) throws UnknownHostException;

}
