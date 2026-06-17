package com.ut.emrPacs.config;

import com.ut.emrPacs.helper.security.SecurityIncidentReporter;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisSqlInjectionGuardInterceptorTest {

    @Test
    void reportsUnsafeDynamicSqlParameterBeforeRejecting() throws Throwable {
        SecurityIncidentReporter securityIncidentReporter = mock(SecurityIncidentReporter.class);
        MyBatisSqlInjectionGuardInterceptor interceptor = new MyBatisSqlInjectionGuardInterceptor(securityIncidentReporter);
        Invocation invocation = mock(Invocation.class);
        MappedStatement mappedStatement = mock(MappedStatement.class);
        when(mappedStatement.getId()).thenReturn("StudyMapper.list");
        when(invocation.getArgs()).thenReturn(new Object[]{
                mappedStatement,
                Map.of("orderBy", "created desc; drop table users")
        });

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> interceptor.intercept(invocation));

        assertTrue(error.getMessage().contains("StudyMapper.list"));
        verify(securityIncidentReporter).reportBlockedRequest(
                isNull(),
                eq("sql_injection_guard"),
                eq("unsafe_dynamic_sql"),
                contains("StudyMapper.list")
        );
        verify(invocation, never()).proceed();
    }
}
