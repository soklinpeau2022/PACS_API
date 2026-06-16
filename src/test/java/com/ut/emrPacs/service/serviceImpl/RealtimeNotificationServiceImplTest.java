package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.mapper.notification.RealtimeNotificationMapper;
import com.ut.emrPacs.model.dto.response.notification.RealtimeNotificationEvent;
import com.ut.emrPacs.model.dto.response.pacs.study.StudyResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeNotificationServiceImplTest {

    @Mock
    private RealtimeNotificationMapper realtimeNotificationMapper;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void subscribeShouldRequireHospitalContext() {
        RealtimeNotificationServiceImpl service = new RealtimeNotificationServiceImpl(realtimeNotificationMapper);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.subscribe(null));

        assertTrue(error.getMessage().contains("Hospital context"));
    }

    @Test
    void freshSubscribeShouldStartAtHospitalLatestCursorAndPollOnlyThatHospital() {
        authenticate(7L, 11L);
        when(realtimeNotificationMapper.findLatestEventId(11L)).thenReturn(42L);
        when(realtimeNotificationMapper.listEventsAfter(11L, 42L, 200)).thenReturn(List.of());
        RealtimeNotificationServiceImpl service = new RealtimeNotificationServiceImpl(realtimeNotificationMapper);

        service.subscribe(null);
        service.dispatchPendingEvents();

        verify(realtimeNotificationMapper).findLatestEventId(11L);
        verify(realtimeNotificationMapper).listEventsAfter(11L, 42L, 200);
    }

    @Test
    void subscribeShouldClampCursorAheadOfDatabaseAfterReset() {
        authenticate(7L, 11L);
        when(realtimeNotificationMapper.findLatestEventId(11L)).thenReturn(8L);
        when(realtimeNotificationMapper.listEventsAfter(11L, 8L, 200)).thenReturn(List.of());
        RealtimeNotificationServiceImpl service = new RealtimeNotificationServiceImpl(realtimeNotificationMapper);

        service.subscribe(999L);
        service.dispatchPendingEvents();

        verify(realtimeNotificationMapper).listEventsAfter(11L, 8L, 200);
    }

    @Test
    void publishStudyShouldPersistDedupedHospitalScopedEvent() {
        RealtimeNotificationServiceImpl service = new RealtimeNotificationServiceImpl(realtimeNotificationMapper);
        StudyResponse study = new StudyResponse();
        study.setId(91L);
        study.setHospitalId(11L);
        study.setPublicKey("study-key");
        study.setPatientName("HEL SOK");
        study.setAccessionNumber("ACC-1");

        service.publishImageReceived(study, "Uploaded");

        ArgumentCaptor<RealtimeNotificationEvent> captor = ArgumentCaptor.forClass(RealtimeNotificationEvent.class);
        verify(realtimeNotificationMapper).insertEvent(captor.capture());
        RealtimeNotificationEvent event = captor.getValue();
        assertEquals(11L, event.getHospitalId());
        assertEquals(91L, event.getStudyId());
        assertEquals("STUDY", event.getSource());
        assertEquals("IMAGE_RECEIVED", event.getType());
        assertEquals("IMAGE_RECEIVED:STUDY:91", event.getDedupeKey());
        assertEquals("study-key", event.getStudyPublicKey());
    }

    @Test
    void cleanupShouldUseConfiguredRetentionWindow() {
        RealtimeNotificationServiceImpl service = new RealtimeNotificationServiceImpl(realtimeNotificationMapper);
        ReflectionTestUtils.setField(service, "eventRetentionDays", 9);

        service.cleanupOldEvents();

        verify(realtimeNotificationMapper).deleteEventsOlderThan(eq(9));
    }

    @Test
    void publishFailureShouldNeverBreakClinicalWorkflow() {
        RealtimeNotificationServiceImpl service = new RealtimeNotificationServiceImpl(realtimeNotificationMapper);
        StudyResponse study = new StudyResponse();
        study.setId(91L);
        study.setHospitalId(11L);
        doThrow(new IllegalStateException("database unavailable")).when(realtimeNotificationMapper).insertEvent(any());

        assertDoesNotThrow(() -> service.publishImageReceived(study, "Uploaded"));
    }

    private static void authenticate(Long userId, Long hospitalId) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("admin", "n/a", "ROLE_ADMIN");
        auth.setAuthenticated(true);
        auth.setDetails(new CurrentUserPrincipal(userId, "admin", hospitalId, "HSP", "pacs-web", "jti", 1L));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
