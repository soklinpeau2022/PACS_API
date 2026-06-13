package com.ut.emrPacs.mapper.permission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PermissionMapper {
    List<String> listPermissionCodes(@Param("userId") Long userId, @Param("hospitalId") Long hospitalId);
    Long countActiveModuleDetailsByIds(@Param("moduleDetailIds") List<Long> moduleDetailIds);
}
