package com.ut.emrPacs.mapper.user;


import com.ut.emrPacs.model.dto.response.authentication.module.ModuleType;
import com.ut.emrPacs.model.dto.response.permission.RolePermissionTreeResponse;
import com.ut.emrPacs.model.base.filter.ModuleTypeFilter;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MyBatis mapper interface for namespace {@code com.ut.emrPacs.mapper.user.ModuleTypeMapper}.
 * Each method maps to an XML statement with the same {@code id}.
 */
@Mapper
public interface ModuleTypeMapper {

    /**
     * MyBatis statement id: {@code getList}.
     */
    List<ModuleType> listModuleType(ModuleTypeFilter filter);

    /**
     * MyBatis statement id: {@code countList}.
     */
    Long countList(ModuleTypeFilter filter);

    /**
     * MyBatis statement id: {@code getOne}.
     */
    List<ModuleType> getModuleTypeById(Long id);

    List<RolePermissionTreeResponse> getAllActiveModuleTreeFlat(@Param("userId") Long userId, @Param("hospitalId") Long hospitalId);

}
