package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultContextRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultFindByWorklistRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultFindByStudyRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultImageRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultImageUploadRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultSaveRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultTemplateListRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.net.UnknownHostException;
import java.util.List;

public interface PacsResultService {
    boolean hasStaticResultAuth(HttpServletRequest request);

    ResponseMessage<BaseResult> create(PacsResultSaveRequest request,
                                       List<MultipartFile> images,
                                       HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> findByStudy(PacsResultFindByStudyRequest request,
                                            HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> findByWorklist(PacsResultFindByWorklistRequest request,
                                            HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getContext(PacsResultContextRequest request,
                                           HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> update(PacsResultSaveRequest request,
                                       List<MultipartFile> images,
                                       HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> uploadImages(PacsResultImageUploadRequest request,
                                             List<MultipartFile> images,
                                             HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deleteImage(PacsResultImageRequest request,
                                            HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseEntity<Resource> readImage(PacsResultImageRequest request, HttpServletRequest httpServletRequest);

    ResponseEntity<Resource> readHospitalLogo(PacsResultContextRequest request, HttpServletRequest httpServletRequest);

    ResponseMessage<BaseResult> listTemplates(PacsResultTemplateListRequest request,
                                              HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> findTemplate(String templateKey,
                                             PacsResultTemplateListRequest request,
                                             HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> findViewerState(PacsViewerStateRequest request,
                                                HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> saveViewerState(PacsViewerStateRequest request,
                                                HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deleteViewerState(PacsViewerStateRequest request,
                                                  HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> findBrowserViewerState(PacsViewerStateRequest request,
                                                       HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> saveBrowserViewerState(PacsViewerStateRequest request,
                                                       HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deleteBrowserViewerState(PacsViewerStateRequest request,
                                                         HttpServletRequest httpServletRequest) throws UnknownHostException;
}
