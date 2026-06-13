package com.ut.emrPacs.mapper.auth;

import com.ut.emrPacs.model.users.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {

    User findOneByEmail(String email);

    User findUserDetails(String username);

    User findAuthUserById(@Param("userId") Long userId);

    Long findDefaultHospitalIdByUserId(@Param("userId") Long userId);

    String findHospitalCodeByHospitalId(@Param("hospitalId") Long hospitalId);

    Long findPermissionVersionByUserId(@Param("userId") Long userId);
}
