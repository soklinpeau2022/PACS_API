package com.ut.emrPacs.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorklistStatusTest {

    @Test
    void shouldAllowValidTransitions() {
        assertTrue(WorklistStatus.WAITING.canTransitionTo(WorklistStatus.IN_PROGRESS));
        assertTrue(WorklistStatus.FAILED.canTransitionTo(WorklistStatus.IN_PROGRESS));
        assertTrue(WorklistStatus.FAILED.canTransitionTo(WorklistStatus.CANCELLED));
        assertTrue(WorklistStatus.WAITING.canTransitionTo(WorklistStatus.FAILED));
    }

    @Test
    void shouldBlockInvalidTransitions() {
        assertFalse(WorklistStatus.CANCELLED.canTransitionTo(WorklistStatus.WAITING));
        assertFalse(WorklistStatus.IN_PROGRESS.canTransitionTo(WorklistStatus.WAITING));
        assertFalse(WorklistStatus.CANCELLED.canTransitionTo(WorklistStatus.FAILED));
        assertFalse(WorklistStatus.IN_PROGRESS.canTransitionTo(WorklistStatus.CANCELLED));
    }
}
