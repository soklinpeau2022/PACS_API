package com.ut.emrPacs.authentication.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicViewerAttemptGuardTest {

    private PublicViewerAttemptGuard guard;

    @BeforeEach
    void setUp() {
        guard = new PublicViewerAttemptGuard();
        ReflectionTestUtils.setField(guard, "windowSeconds", 900);
        ReflectionTestUtils.setField(guard, "maxFailures", 2);
        guard.initialize();
    }

    @Test
    void blocksRepeatedFailuresForTheSamePublicLink() {
        guard.recordFailure("hospital-a", "worklist-a");
        assertFalse(guard.isBlocked("hospital-a", "worklist-a"));

        guard.recordFailure("hospital-a", "worklist-a");
        assertTrue(guard.isBlocked("hospital-a", "worklist-a"));
        assertFalse(guard.isBlocked("hospital-a", "worklist-b"));
    }

    @Test
    void successfulVerificationClearsTheLinkFailureWindow() {
        guard.recordFailure("hospital-a", "worklist-a");
        guard.recordFailure("hospital-a", "worklist-a");
        guard.clear("hospital-a", "worklist-a");

        assertFalse(guard.isBlocked("hospital-a", "worklist-a"));
    }
}
