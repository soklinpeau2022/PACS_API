package com.ut.emrPacs.mapper.pacs;

import com.ut.emrPacs.model.base.filter.WorklistFilter;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistAssignRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistActionRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistUpdateRequest;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistListResponse;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface WorklistMapper {
    Long countList(@Param("hospitalId") Long hospitalId, @Param("filter") WorklistFilter filter);

    List<WorklistListResponse> list(@Param("hospitalId") Long hospitalId, @Param("filter") WorklistFilter filter);

    Long nextVisitSequence(@Param("hospitalId") Long hospitalId, @Param("sequenceKey") String sequenceKey);

    Boolean assignWorklist(@Param("hospitalId") Long hospitalId,
                        @Param("createdBy") Long createdBy,
                        @Param("visitCode") String visitCode,
                        @Param("request") WorklistAssignRequest request);

    Long countPatientModalityActiveWorklist(@Param("hospitalId") Long hospitalId,
                                        @Param("patientId") Long patientId,
                                        @Param("modalityId") Long modalityId);

    Integer findStatusCodeById(@Param("hospitalId") Long hospitalId, @Param("id") Long id);

    WorklistDetailRow findWorklistById(@Param("hospitalId") Long hospitalId, @Param("id") Long id);

    WorklistDetailRow findWorklistByIdAnyHospital(@Param("id") Long id);

    WorklistDetailRow findWorklistByVisitCode(@Param("hospitalId") Long hospitalId, @Param("visitCode") String visitCode);

    WorklistDetailRow findWorklistByVisitCodeAnyHospital(@Param("visitCode") String visitCode);

    WorklistDetailRow findWorklistByAccessionNumber(@Param("accessionNumber") String accessionNumber);
    WorklistDetailRow findWorklistByAccessionNumberAndHospital(@Param("hospitalId") Long hospitalId, @Param("accessionNumber") String accessionNumber);
    WorklistDetailRow findWorklistByStudyIdentifiers(@Param("studyInstanceUid") String studyInstanceUid,
                                                     @Param("dicomServerStudyId") String dicomServerStudyId);

    List<WorklistDetailRow> listWaitingWorklistForAutoSend(@Param("limit") Integer limit);
    List<WorklistDetailRow> listPendingPacsSyncWorklist();

    int updateStatus(@Param("hospitalId") Long hospitalId, @Param("request") WorklistActionRequest request, @Param("statusCode") Integer statusCode);

    int updateWorklistStatusById(@Param("hospitalId") Long hospitalId,
                              @Param("id") Long id,
                              @Param("statusCode") Integer statusCode,
                              @Param("modifiedBy") Long modifiedBy);

    int updateWorklistWorkflowStatusById(@Param("hospitalId") Long hospitalId,
                                      @Param("id") Long id,
                                      @Param("statusCode") Integer statusCode,
                                      @Param("errorMessage") String errorMessage,
                                      @Param("modifiedBy") Long modifiedBy);

    int updateWorklistSentToPacsById(@Param("hospitalId") Long hospitalId,
                                  @Param("id") Long id,
                                  @Param("statusCode") Integer statusCode,
                                  @Param("dicomServerId") Long dicomServerId,
                                  @Param("dicomRouteId") Long dicomRouteId,
                                  @Param("accessionNumber") String accessionNumber,
                                  @Param("modalityCode") String modalityCode,
                                  @Param("machineAeTitle") String machineAeTitle,
                                  @Param("studyDescription") String studyDescription,
                                  @Param("scheduledDate") LocalDate scheduledDate,
                                  @Param("scheduledTime") LocalTime scheduledTime,
                                  @Param("worklistId") String worklistId,
                                  @Param("worklistPath") String worklistPath,
                                  @Param("modifiedBy") Long modifiedBy);

    int updateWorklistEditableFieldsById(@Param("hospitalId") Long hospitalId,
                                      @Param("id") Long id,
                                      @Param("request") WorklistUpdateRequest request,
                                      @Param("dicomServerId") Long dicomServerId,
                                      @Param("studyDescription") String studyDescription,
                                      @Param("scheduledDate") LocalDate scheduledDate,
                                      @Param("scheduledTime") LocalTime scheduledTime,
                                      @Param("modifiedBy") Long modifiedBy);

    int updateWorklistDicomWorklistFieldsById(@Param("hospitalId") Long hospitalId,
                                      @Param("id") Long id,
                                      @Param("modalityId") Long modalityId,
                                      @Param("accessionNumber") String accessionNumber,
                                      @Param("modalityCode") String modalityCode,
                                      @Param("machineAeTitle") String machineAeTitle,
                                      @Param("studyDescription") String studyDescription,
                                      @Param("scheduledDate") LocalDate scheduledDate,
                                      @Param("scheduledTime") LocalTime scheduledTime,
                                      @Param("worklistId") String worklistId,
                                      @Param("worklistPath") String worklistPath,
                                      @Param("modifiedBy") Long modifiedBy);

    int updateWorklistDicomRouteIdById(@Param("hospitalId") Long hospitalId,
                                    @Param("id") Long id,
                                    @Param("dicomRouteId") Long dicomRouteId,
                                    @Param("modifiedBy") Long modifiedBy);

    int updateWorklistReceivedByVisitCode(@Param("hospitalId") Long hospitalId,
                                       @Param("visitCode") String visitCode,
                                       @Param("studyId") Long studyId,
                                       @Param("receivedAtIso") String receivedAtIso,
                                       @Param("statusCode") Integer statusCode,
                                       @Param("modifiedBy") Long modifiedBy);

    int updateWorklistReceivedById(@Param("hospitalId") Long hospitalId,
                                @Param("id") Long id,
                                @Param("studyId") Long studyId,
                                @Param("statusCode") Integer statusCode,
                                @Param("modifiedBy") Long modifiedBy,
                                @Param("receivedAtIso") String receivedAtIso);

    int updateWorklistReceivedFromCallbackById(@Param("hospitalId") Long hospitalId,
                                            @Param("id") Long id,
                                            @Param("studyId") Long studyId,
                                            @Param("statusCode") Integer statusCode,
                                            @Param("modifiedBy") Long modifiedBy,
                                            @Param("receivedAtIso") String receivedAtIso);

    int updateWorklistViewerStudyIdentifiers(@Param("hospitalId") Long hospitalId,
                                          @Param("id") Long id,
                                          @Param("studyId") Long studyId,
                                          @Param("modifiedBy") Long modifiedBy);

    int upsertWorklistStudyLink(@Param("hospitalId") Long hospitalId,
                             @Param("worklistId") Long worklistId,
                             @Param("studyId") Long studyId,
                             @Param("createdBy") Long createdBy);

    int insertHistory(@Param("hospitalId") Long hospitalId,
                      @Param("worklistId") Long worklistId,
                      @Param("patientId") Long patientId,
                      @Param("fromStatusCode") Integer fromStatusCode,
                      @Param("toStatusCode") Integer toStatusCode,
                      @Param("action") String action,
                      @Param("reason") String reason,
                      @Param("createdBy") Long createdBy);
}
