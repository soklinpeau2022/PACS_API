package com.ut.emrPacs.mapper.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper interface for namespace {@code com.ut.emrPacs.mapper.user.UserPermissionMapper}.
 * Each method maps to an XML statement with the same {@code id}.
 */
@Mapper
public interface UserPermissionMapper {

    /**
     * MyBatis statement id: {@code insert}.
     */
    Boolean createPermission(@Param("roleId") Long roleId, @Param("moduleId") Long moduleId);

    /**
     * MyBatis statement id: {@code delete}.
     */
    Boolean deletePermission(Long roleId);

}
