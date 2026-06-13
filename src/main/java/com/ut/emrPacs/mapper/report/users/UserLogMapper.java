package com.ut.emrPacs.mapper.report.users;

import com.ut.emrPacs.model.base.filter.UserLogFilter;
import com.ut.emrPacs.model.dto.response.reports.users.UserLogResponse;

import java.util.List;

public interface UserLogMapper {
    List<UserLogResponse> listUserLog(UserLogFilter filter);
    Long countList(UserLogFilter filter);
    List<UserLogResponse> getUserLogById(Long id);
}
