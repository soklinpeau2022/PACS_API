package com.ut.emrPacs.helper.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestPayloadGuardTest {

    @Test
    void shouldAllowSafePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("table", "patients");
        payload.put("field", "p.id");
        payload.put("orderBy", "id DESC");
        payload.put("page", 1);
        payload.put("rowsPerPage", 20);
        payload.put("moduleIds", "1,2,3");

        assertDoesNotThrow(() -> RequestPayloadGuard.validate(payload));
    }

    @Test
    void shouldRejectNegativePaginationOrIdValues() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("page", -1);
        payload.put("rowsPerPage", 10);

        assertThrows(IllegalArgumentException.class, () -> RequestPayloadGuard.validate(payload));
    }

    @Test
    void shouldRejectUnsafeOrderBy() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderBy", "id desc; drop table users");

        assertThrows(IllegalArgumentException.class, () -> RequestPayloadGuard.validate(payload));
    }

    @Test
    void shouldRejectInvalidIdList() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userIds", "1,2,a");

        assertThrows(IllegalArgumentException.class, () -> RequestPayloadGuard.validate(payload));
    }

    @Test
    void shouldAllowDicomServerSeriesIdsArrayTokens() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accessionNumber", "VIEW-SM-0005");
        payload.put("dicomServerSeriesIds", List.of("40d1ffe1-e43ebeda-5f7d0282-02589bd1-afeff9b4"));

        assertDoesNotThrow(() -> RequestPayloadGuard.validate(payload));
    }
}
