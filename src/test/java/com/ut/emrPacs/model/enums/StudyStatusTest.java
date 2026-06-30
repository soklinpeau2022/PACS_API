package com.ut.emrPacs.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StudyStatusTest {

    @Test
    void shouldExposeOnlyStudyWorkflowStatuses() {
        assertEquals(1, StudyStatus.IMAGE_RECEIVED.code());
        assertEquals(2, StudyStatus.COMPLETED.code());
    }

    @Test
    void shouldParseStudyStatusValues() {
        assertEquals(StudyStatus.IMAGE_RECEIVED, StudyStatus.fromValue("image received"));
        assertEquals(StudyStatus.COMPLETED, StudyStatus.fromValue("COMPLETED"));
        assertEquals(1, StudyStatus.codeOfNullable("1"));
        assertEquals(2, StudyStatus.codeOfNullable("2"));
    }

    @Test
    void shouldRejectWorklistStatuses() {
        assertThrows(IllegalArgumentException.class, () -> StudyStatus.fromValue("IN_PROGRESS"));
        assertThrows(IllegalArgumentException.class, () -> StudyStatus.fromValue("WAITING"));
        assertThrows(IllegalArgumentException.class, () -> StudyStatus.fromValue("CANCELLED"));
        assertThrows(IllegalArgumentException.class, () -> StudyStatus.fromValue("FAILED"));
    }
}
