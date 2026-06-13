package com.ut.emrPacs.mapper.dropDown;

import com.ut.emrPacs.model.base.filter.DropDownFilter;
import com.ut.emrPacs.model.dto.response.dropDown.DicomServerDropDownResponse;
import com.ut.emrPacs.model.dto.response.dropDown.DropDownModelResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DropDownMapper {

    List<DropDownModelResponse> getListCountries(@Param("filter") DropDownFilter filter);

    List<DropDownModelResponse> getListHospitalsByUserId(@Param("userId") Long userId, @Param("filter") DropDownFilter filter);

    List<DropDownModelResponse> getListModalitiesByHospitalId(@Param("hospitalId") Long hospitalId, @Param("filter") DropDownFilter filter);

    List<DropDownModelResponse> getListModalityCatalog(@Param("filter") DropDownFilter filter);

    List<DicomServerDropDownResponse> getListDicomServersByHospitalId(@Param("hospitalId") Long hospitalId, @Param("filter") DropDownFilter filter);

    List<DropDownModelResponse> getListUsers(
            @Param("hospitalId") Long hospitalId,
            @Param("filter") DropDownFilter filter
    );

    List<DropDownModelResponse> getListPatientsByHospitalId(
            @Param("hospitalId") Long hospitalId,
            @Param("filter") DropDownFilter filter
    );

    List<DropDownModelResponse> getListUserGroups(
            @Param("hospitalId") Long hospitalId,
            @Param("actorHospitalId") Long actorHospitalId,
            @Param("includeAllHospitals") Boolean includeAllHospitals,
            @Param("includeCrossScope") Boolean includeCrossScope,
            @Param("filter") DropDownFilter filter
    );
}
