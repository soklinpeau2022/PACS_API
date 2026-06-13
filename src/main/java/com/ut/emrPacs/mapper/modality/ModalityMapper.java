package com.ut.emrPacs.mapper.modality;

import com.ut.emrPacs.model.base.filter.ModalityFilter;
import com.ut.emrPacs.model.components.systemSettings.modality.HospitalModalityRelation;
import com.ut.emrPacs.model.components.systemSettings.modality.Modality;
import com.ut.emrPacs.model.dto.response.systemSettings.modality.HospitalModalityFlatRow;
import com.ut.emrPacs.model.dto.response.systemSettings.modality.ModalityResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ModalityMapper {
    Long countModality(ModalityFilter filter);

    List<ModalityResponse> listModality(ModalityFilter filter);

    List<ModalityResponse> getModalityById(Long id);

    Boolean createModality(Modality modality);

    Boolean updateModality(Modality modality);

    Boolean deleteModality(@Param("id") Long id, @Param("modifiedBy") Long modifiedBy);

    List<String> listActiveDicomRouteUsageByModalityId(@Param("id") Long id);

    List<String> listActiveMachineUsageByModalityId(@Param("id") Long id);

    Long countActiveModalityByName(@Param("name") String name, @Param("excludeId") Long excludeId);

    Long countActiveModalityByAbbr(@Param("abbr") String abbr, @Param("excludeId") Long excludeId);

    Long countActiveModalitiesByIds(@Param("modalityIds") List<Long> modalityIds);

    List<String> findActiveModalityNamesByIds(@Param("modalityIds") List<Long> modalityIds);

    Long countActiveHospitalModality(@Param("hospitalId") Long hospitalId, @Param("modalityId") Long modalityId);

    Boolean deactivateHospitalModalityByHospitalId(@Param("hospitalId") Long hospitalId, @Param("modifiedBy") Long modifiedBy);

    Boolean insertHospitalModalitiesBatch(@Param("relations") List<HospitalModalityRelation> relations);

    List<HospitalModalityFlatRow> listHospitalModalityByUserId(@Param("userId") Long userId);
}
