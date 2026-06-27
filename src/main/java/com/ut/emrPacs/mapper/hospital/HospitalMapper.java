package com.ut.emrPacs.mapper.hospital;

import java.util.List;

import com.ut.emrPacs.model.dto.response.systemSettings.hospital.*;
import org.apache.ibatis.annotations.Mapper;

import com.ut.emrPacs.model.base.filter.HospitalListFilter;
import com.ut.emrPacs.model.components.systemSettings.hospital.Hospital;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper interface for namespace {@code com.ut.emrPacs.mapper.hospital.HospitalMapper}.
 * Each method maps to an XML statement with the same {@code id}.
 */
@Mapper
public interface HospitalMapper {

    /**
     * MyBatis statement id: {@code getList}.
     */
    List<HospitalResponse> listHospital(HospitalListFilter filter);

    Long countHospitalList(HospitalListFilter filter);

    /**
     * MyBatis statement id: {@code getOne}.
     */
    List<HospitalResponseDetail> getHospitalById(Long id);

    /**
     * MyBatis statement id: {@code getUserName}.
     */
    List<UserNameResponse> getUserName(Long hospitalId);

    /**
     * MyBatis statement id: {@code getHospitalUserList}.
     */
    List<HospitalUserList> getHospitalUserList(Long hospitalId);

    /**
     * MyBatis statement id: {@code countHospitalCode}.
     */
    Long countHospitalCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    Long countHospitalAbbr(@Param("abbr") String abbr, @Param("excludeId") Long excludeId);

    Long countHospitalVisitToken(@Param("visitToken") String visitToken, @Param("excludeId") Long excludeId);

    /**
     * MyBatis statement id: {@code createHospital}.
     */
    Boolean createHospital(Hospital hospital);

    /**
     * MyBatis statement id: {@code update}.
     */
    Boolean updateHospital(Hospital hospital);

    Boolean updateHospitalLogo(Hospital hospital);

    /**
     * MyBatis statement id: {@code deleteHospitalUser}.
     */
    Boolean deleteHospitalUser(Long hospitalId);

    /**
     * MyBatis statement id: {@code insertHospitalUser}.
     */
    Boolean insertHospitalUser(@Param("userId") Long userId, @Param("hospitalId") Long hospitalId);
}
