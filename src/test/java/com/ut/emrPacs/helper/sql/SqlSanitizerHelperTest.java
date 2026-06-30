package com.ut.emrPacs.helper.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlSanitizerHelperTest {

    @Test
    void sanitizeOrderByShouldAllowSafeColumnsAndDirections() {
        String sanitized = SqlSanitizerHelper.sanitizeOrderBy("id DESC, created_at ASC");
        assertEquals("id DESC, created_at ASC", sanitized);
    }

    @Test
    void sanitizeOrderByShouldRejectInjectionPayloads() {
        assertNull(SqlSanitizerHelper.sanitizeOrderBy("id; drop table users"));
        assertNull(SqlSanitizerHelper.sanitizeOrderBy("id -- comment"));
    }

    @Test
    void requireSafeIdentifierShouldEnforceStrictPattern() {
        assertEquals("patient_name", SqlSanitizerHelper.requireSafeIdentifier("patient_name"));
        assertThrows(IllegalArgumentException.class, () -> SqlSanitizerHelper.requireSafeIdentifier("patient-name"));
    }

    @Test
    void requireSafeDottedIdentifierShouldValidateSegments() {
        assertEquals("p.name", SqlSanitizerHelper.requireSafeDottedIdentifier("p.name"));
        assertThrows(IllegalArgumentException.class, () -> SqlSanitizerHelper.requireSafeDottedIdentifier("p..name"));
    }
}
