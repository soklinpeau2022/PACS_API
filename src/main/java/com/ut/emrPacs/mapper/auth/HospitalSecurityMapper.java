package com.ut.emrPacs.mapper.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HospitalSecurityMapper {

    Long countActiveHospitalById(@Param("hospitalId") Long hospitalId);

    Long countActiveUserHospital(@Param("userId") Long userId, @Param("hospitalId") Long hospitalId);
}

