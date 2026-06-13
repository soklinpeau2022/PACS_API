package com.ut.emrPacs.mapper.pacs;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DicomServerCallbackLogMapper {

    int insertCallbackLog(@Param("event") String event,
                          @Param("accessionNumber") String accessionNumber,
                          @Param("dicomServerStudyId") String dicomServerStudyId,
                          @Param("dicomServerPatientId") String dicomServerPatientId,
                          @Param("dicomServerSeriesIdsJson") String dicomServerSeriesIdsJson,
                          @Param("payloadJson") String payloadJson,
                          @Param("success") Boolean success,
                          @Param("errorMessage") String errorMessage,
                          @Param("warningMessage") String warningMessage,
                          @Param("receivedAtIso") String receivedAtIso);
}
