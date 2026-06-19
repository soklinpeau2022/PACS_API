package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.mapper.log.ActivityLogMapper;
import com.ut.emrPacs.mapper.report.users.UserLogMapper;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.filter.ActivityFilter;
import com.ut.emrPacs.model.base.filter.UserLogFilter;
import com.ut.emrPacs.model.users.User;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LargeLogCursorPaginationTest {

    @Mock
    private ActivityLogMapper activityLogMapper;
    @Mock
    private UserLogMapper userLogMapper;
    @Mock
    private ActivityLogService auditWriter;
    @Mock
    private UserService userService;

    @Test
    void activityCursorShouldSkipCount() throws Exception {
        ActivityLogServiceImpl service = new ActivityLogServiceImpl();
        ReflectionTestUtils.setField(service, "activityLogMapper", activityLogMapper);
        ReflectionTestUtils.setField(service, "activityLogService", auditWriter);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());

        ActivityFilter filter = new ActivityFilter();
        filter.setLastActivityId(100_000L);
        filter.setRowsPerPage(20);
        when(activityLogMapper.listActivityLog(filter)).thenReturn(List.of());

        var response = service.listActivityLog(filter, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(activityLogMapper, never()).countList(filter);
        verify(activityLogMapper).listActivityLog(filter);
    }

    @Test
    void userLogCursorShouldSkipCount() throws Exception {
        UserLogServiceImpl service = new UserLogServiceImpl();
        ReflectionTestUtils.setField(service, "userLogMapper", userLogMapper);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "activityLogService", auditWriter);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());

        User user = new User();
        user.setId(9L);
        when(userService.getUserAuth()).thenReturn(user);

        UserLogFilter filter = new UserLogFilter();
        filter.setLastUserLogId(100_000L);
        filter.setRowsPerPage(20);
        when(userLogMapper.listUserLog(filter)).thenReturn(List.of());

        var response = service.listUserLog(filter, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(userLogMapper, never()).countList(filter);
        verify(userLogMapper).listUserLog(filter);
    }
}

