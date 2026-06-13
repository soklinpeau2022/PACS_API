package com.ut.emrPacs.mapper.user;

import org.apache.ibatis.annotations.Mapper;
import com.ut.emrPacs.model.role.Module;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper interface for namespace {@code com.ut.emrPacs.mapper.user.ModuleMapper}.
 * Each method maps to an XML statement with the same {@code id}.
 */
@Mapper
public interface ModuleMapper {

    /**
     * MyBatis statement id: {@code getOne}.
     */
    List<Module> getModuleById(Long id);

    /**
     * MyBatis statement id: {@code getOneByRoleId}.
     */
    List<Module> getOneByRoleId(@Param("id") Long id, @Param("roleId") Long roleId);

    /**
     * MyBatis statement id: {@code getOneByUserId}.
     */
    List<Module> getOneByUserId(@Param("id") Long id, @Param("userId") Long userId);

}