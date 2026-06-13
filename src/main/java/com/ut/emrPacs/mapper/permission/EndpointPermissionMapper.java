package com.ut.emrPacs.mapper.permission;

import com.ut.emrPacs.model.permission.EndpointPermissionRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EndpointPermissionMapper {
    List<EndpointPermissionRule> listActiveRules();
}
