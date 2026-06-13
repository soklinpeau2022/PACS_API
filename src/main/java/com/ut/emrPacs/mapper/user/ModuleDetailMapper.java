package com.ut.emrPacs.mapper.user;

import com.ut.emrPacs.model.dto.response.permission.ModuleDetailResponse;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ModuleDetailMapper {
    List<ModuleDetailResponse> listModuleDetailByModuleType(@Param("moduleTypeId") Long moduleTypeId);
}
