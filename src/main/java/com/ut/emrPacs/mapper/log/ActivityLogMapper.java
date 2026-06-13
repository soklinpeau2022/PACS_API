package com.ut.emrPacs.mapper.log;

import com.ut.emrPacs.model.log.ActivityLog;
import com.ut.emrPacs.model.base.filter.ActivityFilter;

import java.util.List;

public interface ActivityLogMapper {

    List<ActivityLog> listActivityLog(ActivityFilter filter);

    Long countList(ActivityFilter filter);

    List<ActivityLog> getActivityLogById(Long id);

    Boolean createActivityLog(ActivityLog activityLog);

    Long getModuleId(String moduleName);

    String getFullName(Long userId);
}
