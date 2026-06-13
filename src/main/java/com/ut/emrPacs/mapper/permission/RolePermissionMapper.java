package com.ut.emrPacs.mapper.permission;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RolePermissionMapper {
    int deleteByRoleId(@Param("roleId") Long roleId);
    void insert(@Param("roleId") Long roleId, @Param("moduleDetailId") Long moduleDetailId, @Param("createdBy") Long createdBy);
    void insertBatch(@Param("roleId") Long roleId, @Param("moduleDetailIds") List<Long> moduleDetailIds, @Param("createdBy") Long createdBy);
    List<Long> findUserIdsByRoleId(@Param("roleId") Long roleId);
    void bumpPermissionVersion(@Param("userId") Long userId);
}
