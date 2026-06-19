package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.mapper.pacs.StudyMapper;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.filter.StudyListFilter;
import com.ut.emrPacs.service.service.ActivityLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyServiceImplListPaginationTest {

    @Mock
    private StudyMapper studyMapper;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cursorPagingShouldSkipExpensiveCountQuery() throws Exception {
        StudyServiceImpl service = new StudyServiceImpl();
        ReflectionTestUtils.setField(service, "studyMapper", studyMapper);
        ReflectionTestUtils.setField(service, "activityLogService", activityLogService);
        ReflectionTestUtils.setField(service, "publicEntityKeyResolver", publicEntityKeyResolver);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());

        var authentication = new UsernamePasswordAuthenticationToken("user", "n/a");
        authentication.setDetails(new CurrentUserPrincipal(
                9L,
                "user",
                11L,
                "HSP001",
                "pacs-web",
                "jti",
                1L
        ));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        StudyListFilter filter = new StudyListFilter();
        filter.setLastStudyId(500_000L);
        filter.setRowsPerPage(20);

        when(studyMapper.listWeekCache(11L, filter)).thenReturn(List.of());

        var response = service.list(filter, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(studyMapper, never()).count(eq(11L), eq(filter));
        verify(studyMapper, never()).countWeekCache(eq(11L), eq(filter));
        verify(studyMapper).listWeekCache(11L, filter);
        verify(studyMapper, never()).list(eq(11L), eq(filter));
    }

    @Test
    void accessionSearchShouldReadMainTable() throws Exception {
        StudyServiceImpl service = new StudyServiceImpl();
        ReflectionTestUtils.setField(service, "studyMapper", studyMapper);
        ReflectionTestUtils.setField(service, "activityLogService", activityLogService);
        ReflectionTestUtils.setField(service, "publicEntityKeyResolver", publicEntityKeyResolver);
        ReflectionTestUtils.setField(service, "messageService", new MessageService());

        var authentication = new UsernamePasswordAuthenticationToken("user", "n/a");
        authentication.setDetails(new CurrentUserPrincipal(
                9L,
                "user",
                11L,
                "HSP001",
                "pacs-web",
                "jti",
                1L
        ));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        StudyListFilter filter = new StudyListFilter();
        filter.setAccessionNumberExact("CT-OLD-0001");
        filter.setRowsPerPage(20);

        when(studyMapper.list(11L, filter)).thenReturn(List.of());

        var response = service.list(filter, new MockHttpServletRequest());

        assertTrue(response.isSuccess());
        verify(studyMapper).list(11L, filter);
        verify(studyMapper, never()).listWeekCache(eq(11L), eq(filter));
    }
}
