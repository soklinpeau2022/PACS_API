package com.ut.emrPacs.mapper.pacs;

import com.ut.emrPacs.model.base.filter.PatientListFilter;
import com.ut.emrPacs.model.dto.request.pacs.patient.PatientCreateRequest;
import com.ut.emrPacs.model.dto.request.pacs.patient.PatientUpdateRequest;
import com.ut.emrPacs.model.dto.response.pacs.patient.PatientResponse;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PatientMapper {
    List<PatientResponse> list(@Param("hospitalId") Long hospitalId,
                               @Param("filter") PatientListFilter filter);
    Long count(@Param("hospitalId") Long hospitalId,
               @Param("filter") PatientListFilter filter);
    PatientResponse findById(@Param("hospitalId") Long hospitalId, @Param("id") Long id);
    PatientResponse findByDemographics(@Param("hospitalId") Long hospitalId,
                                       @Param("firstName") String firstName,
                                       @Param("lastName") String lastName,
                                       @Param("dateOfBirth") java.time.LocalDate dateOfBirth,
                                       @Param("gender") String gender);
    PatientResponse findByPatientHn(@Param("hospitalId") Long hospitalId,
                                    @Param("patientHn") String patientHn);
    int updatePatientHnIfBlank(@Param("hospitalId") Long hospitalId,
                               @Param("patientId") Long patientId,
                               @Param("patientHn") String patientHn);
    Long countDuplicatePatientCode(@Param("patientCode") String patientCode, @Param("excludeId") Long excludeId);
    Boolean existsPatientSequenceTable();
    Long nextPatientSequenceByYear(@Param("hospitalId") Long hospitalId,
                                   @Param("yearPrefix") String yearPrefix,
                                   @Param("hospitalToken") String hospitalToken);
    Long maxPatientSequenceByYear(@Param("hospitalId") Long hospitalId,
                                  @Param("yearPrefix") String yearPrefix,
                                  @Param("hospitalToken") String hospitalToken);
    Long create(@Param("hospitalId") Long hospitalId, @Param("request") PatientCreateRequest request);
    Boolean update(@Param("hospitalId") Long hospitalId, @Param("request") PatientUpdateRequest request);
}
