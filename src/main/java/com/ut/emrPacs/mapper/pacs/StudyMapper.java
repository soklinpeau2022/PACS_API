package com.ut.emrPacs.mapper.pacs;

import com.ut.emrPacs.model.base.filter.StudyListFilter;
import com.ut.emrPacs.model.dto.response.pacs.study.StudyResponse;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface StudyMapper {
    Long upsertFromWorklist(@Param("hospitalId") Long hospitalId,
                         @Param("patientId") Long patientId,
                         @Param("studyInstanceUid") String studyInstanceUid,
                         @Param("accessionNumber") String accessionNumber,
                         @Param("modalityId") Long modalityId,
                         @Param("modality") String modality,
                         @Param("studyDate") java.time.LocalDate studyDate,
                         @Param("studyDescription") String studyDescription,
                         @Param("dicomServerId") Long dicomServerId,
                         @Param("statusCode") Integer statusCode,
                         @Param("dicomServerStudyId") String dicomServerStudyId,
                         @Param("dicomServerPatientId") String dicomServerPatientId,
                         @Param("dicomServerSeriesId") String dicomServerSeriesId,
                         @Param("instanceCount") Integer instanceCount,
                         @Param("receivedAtIso") String receivedAtIso);
    Long upsertFromDicomUpload(@Param("hospitalId") Long hospitalId,
                         @Param("patientId") Long patientId,
                         @Param("studyInstanceUid") String studyInstanceUid,
                         @Param("accessionNumber") String accessionNumber,
                         @Param("referenceVisitCode") String referenceVisitCode,
                         @Param("modalityId") Long modalityId,
                         @Param("modality") String modality,
                         @Param("studyDate") java.time.LocalDate studyDate,
                         @Param("studyDescription") String studyDescription,
                         @Param("dicomServerId") Long dicomServerId,
                         @Param("statusCode") Integer statusCode,
                         @Param("dicomServerStudyId") String dicomServerStudyId,
                         @Param("dicomServerPatientId") String dicomServerPatientId,
                         @Param("dicomServerSeriesId") String dicomServerSeriesId,
                         @Param("instanceCount") Integer instanceCount,
                         @Param("uploadedBy") Long uploadedBy,
                         @Param("receivedAtIso") String receivedAtIso);
    int updateStatusById(@Param("hospitalId") Long hospitalId,
                         @Param("studyId") Long studyId,
                         @Param("statusCode") Integer statusCode);
    int updateStatusByWorklistId(@Param("hospitalId") Long hospitalId,
                              @Param("worklistId") Long worklistId,
                              @Param("statusCode") Integer statusCode);
    List<StudyResponse> list(@Param("hospitalId") Long hospitalId,
                             @Param("filter") StudyListFilter filter);
    Long count(@Param("hospitalId") Long hospitalId,
               @Param("filter") StudyListFilter filter);
    StudyResponse findById(@Param("hospitalId") Long hospitalId, @Param("id") Long id);
}
