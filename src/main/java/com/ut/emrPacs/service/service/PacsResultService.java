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
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateChunkCompleteRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateChunkRequest;
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

    ResponseMessage<BaseResult> createBrowser(PacsResultSaveRequest request,
                                              List<MultipartFile> images,
                                              HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> findByStudy(PacsResultFindByStudyRequest request,
                                            HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> findBrowserByStudy(PacsResultFindByStudyRequest request,
                                                   HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> findByWorklist(PacsResultFindByWorklistRequest request,
                                            HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> findBrowserByWorklist(PacsResultFindByWorklistRequest request,
                                                      HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getContext(PacsResultContextRequest request,
                                           HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getBrowserContext(PacsResultContextRequest request,
                                                  HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> update(PacsResultSaveRequest request,
                                       List<MultipartFile> images,
                                       HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> updateBrowser(PacsResultSaveRequest request,
                                              List<MultipartFile> images,
                                              HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> uploadImages(PacsResultImageUploadRequest request,
                                             List<MultipartFile> images,
                                             HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> uploadBrowserImages(PacsResultImageUploadRequest request,
                                                    List<MultipartFile> images,
                                                    HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deleteImage(PacsResultImageRequest request,
                                            HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deleteBrowserImage(PacsResultImageRequest request,
                                                   HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseEntity<Resource> readImage(PacsResultImageRequest request, HttpServletRequest httpServletRequest);

    ResponseEntity<Resource> readBrowserImage(PacsResultImageRequest request, HttpServletRequest httpServletRequest);

    ResponseEntity<Resource> readHospitalLogo(PacsResultContextRequest request, HttpServletRequest httpServletRequest);

    ResponseEntity<Resource> readBrowserHospitalLogo(PacsResultContextRequest request, HttpServletRequest httpServletRequest);

    ResponseMessage<BaseResult> listTemplates(PacsResultTemplateListRequest request,
                                              HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> listBrowserTemplates(PacsResultTemplateListRequest request,
                                                     HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> findTemplate(String templateKey,
                                             PacsResultTemplateListRequest request,
                                             HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> findBrowserTemplate(String templateKey,
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

    ResponseMessage<BaseResult> saveBrowserViewerStateChunk(PacsViewerStateChunkRequest request,
                                                            HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> completeBrowserViewerStateChunk(PacsViewerStateChunkCompleteRequest request,
                                                                HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deleteBrowserViewerState(PacsViewerStateRequest request,
                                                         HttpServletRequest httpServletRequest) throws UnknownHostException;
}
