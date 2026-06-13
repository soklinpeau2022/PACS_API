package com.ut.emrPacs.mapper.pacs;

import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultSaveRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultContextRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultFindByWorklistRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultImageRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultTemplateListRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateRequest;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultContextResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultImageResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultTemplateResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsViewerStateResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PacsResultMapper {
    Long insertResult(@Param("request") PacsResultSaveRequest request,
                      @Param("status") String status,
                      @Param("createdBy") Long createdBy);

    int updateResult(@Param("request") PacsResultSaveRequest request,
                     @Param("status") String status,
                     @Param("modifiedBy") Long modifiedBy);

    PacsResultResponse findById(@Param("resultId") Long resultId);

    PacsResultResponse findByResultKey(@Param("resultKey") String resultKey);

    PacsResultResponse findByStudyId(@Param("hospitalId") Long hospitalId,
                                     @Param("modalityId") Long modalityId,
                                     @Param("studyId") Long studyId);

    PacsResultResponse findByStudyInstanceUid(@Param("hospitalId") Long hospitalId,
                                              @Param("modalityId") Long modalityId,
                                              @Param("studyInstanceUid") String studyInstanceUid);

    PacsResultResponse findByWorklist(@Param("request") PacsResultFindByWorklistRequest request);

    PacsResultContextResponse findContextByWorklistId(@Param("request") PacsResultContextRequest request);

    PacsResultContextResponse findContextByStudyId(@Param("request") PacsResultContextRequest request);

    PacsResultContextResponse findContextByStudyInstanceUid(@Param("request") PacsResultContextRequest request);

    PacsResultContextResponse findContextByAccessionNumber(@Param("request") PacsResultContextRequest request);

    PacsResultResponse findExisting(@Param("request") PacsResultSaveRequest request);

    List<PacsResultImageResponse> listImages(@Param("resultId") Long resultId);

    PacsResultImageResponse findImage(@Param("request") PacsResultImageRequest request);

    int insertImage(@Param("resultId") Long resultId,
                    @Param("hospitalId") Long hospitalId,
                    @Param("modalityId") Long modalityId,
                    @Param("imagePath") String imagePath,
                    @Param("originalFileName") String originalFileName,
                    @Param("fileType") String fileType,
                    @Param("fileSize") Long fileSize,
                    @Param("sortOrder") Integer sortOrder);

    int deactivateImage(@Param("request") PacsResultImageRequest request);

    Integer nextImageSortOrder(@Param("resultId") Long resultId);

    List<PacsResultTemplateResponse> listTemplateOptions(@Param("request") PacsResultTemplateListRequest request);

    PacsResultTemplateResponse findTemplateByIdAndScope(@Param("templateId") Long templateId,
                                                        @Param("request") PacsResultTemplateListRequest request);

    PacsViewerStateResponse findViewerState(@Param("request") PacsViewerStateRequest request);

    Integer lockViewerStateScope(@Param("request") PacsViewerStateRequest request);

    Long insertViewerState(@Param("request") PacsViewerStateRequest request,
                           @Param("createdBy") Long createdBy);

    int updateViewerState(@Param("request") PacsViewerStateRequest request,
                          @Param("modifiedBy") Long modifiedBy);

    int deactivateViewerState(@Param("request") PacsViewerStateRequest request,
                              @Param("modifiedBy") Long modifiedBy);
}
