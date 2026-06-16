package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.mapper.notification.RealtimeNotificationMapper;
import com.ut.emrPacs.model.dto.response.notification.RealtimeNotificationEvent;
import com.ut.emrPacs.model.dto.response.pacs.study.StudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import com.ut.emrPacs.service.service.RealtimeNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RealtimeNotificationServiceImpl implements RealtimeNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeNotificationServiceImpl.class);
    private static final int EVENT_BATCH_SIZE = 200;
    private static final int MAX_CONNECTIONS_PER_USER = 5;
    private static final int MAX_CONNECTIONS_PER_HOSPITAL = 100;
    private static final long EMITTER_TIMEOUT_MS = 5 * 60_000L;

    private final RealtimeNotificationMapper realtimeNotificationMapper;
    private final Map<Long, CopyOnWriteArrayList<Subscriber>> subscribersByHospital = new ConcurrentHashMap<>();

    @Value("${pacs.realtime.event-retention-days:7}")
    private int eventRetentionDays;

    public RealtimeNotificationServiceImpl(RealtimeNotificationMapper realtimeNotificationMapper) {
        this.realtimeNotificationMapper = realtimeNotificationMapper;
    }

    @Override
    public SseEmitter subscribe(Long afterId) {
        CurrentUserPrincipal principal = UserAuthSession.getCurrentUser();
        if (principal == null || principal.hospitalId() == null || principal.hospitalId() <= 0L) {
            throw new IllegalStateException("Hospital context not found in OAuth2 token claims.");
        }

        Long hospitalId = principal.hospitalId();
        long latestEventId = safeLatestEventId(hospitalId);
        long initialCursor = afterId == null || afterId < 0L || afterId > latestEventId
                ? latestEventId
                : afterId;
        CopyOnWriteArrayList<Subscriber> hospitalSubscribers =
                subscribersByHospital.computeIfAbsent(hospitalId, ignored -> new CopyOnWriteArrayList<>());
        trimOldUserConnections(hospitalSubscribers, principal.userId());
        trimOldHospitalConnections(hospitalSubscribers);

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Subscriber subscriber = new Subscriber(principal.userId(), emitter, initialCursor);
        hospitalSubscribers.add(subscriber);

        Runnable remove = () -> removeSubscriber(hospitalId, subscriber);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("ready")
                    .id(String.valueOf(initialCursor))
                    .data(Map.of("cursor", initialCursor)));
            subscriber.markSent();
        } catch (IOException error) {
            remove.run();
        }
        return emitter;
    }

    @Override
    public void publishImageReceived(WorklistDetailRow worklist, Long studyId, String studyPublicKey, String message) {
        if (worklist == null || worklist.getHospitalId() == null || studyId == null) {
            return;
        }
        RealtimeNotificationEvent event = baseImageReceivedEvent(worklist.getHospitalId(), studyId, message);
        event.setWorklistId(worklist.getId());
        event.setWorklistPublicKey(worklist.getPublicKey());
        event.setStudyPublicKey(firstNonBlank(studyPublicKey, worklist.getStudyPublicKey()));
        event.setPatientName(worklist.getPatientName());
        event.setVisitCode(worklist.getVisitCode());
        event.setAccessionNumber(firstNonBlank(worklist.getAccessionNumber(), worklist.getVisitCode()));
        persistQuietly(event);
    }

    @Override
    public void publishImageReceived(StudyResponse study, String message) {
        if (study == null || study.getHospitalId() == null || study.getId() == null) {
            return;
        }
        RealtimeNotificationEvent event = baseImageReceivedEvent(study.getHospitalId(), study.getId(), message);
        event.setStudyPublicKey(study.getPublicKey());
        event.setWorklistId(study.getWorklistId());
        event.setWorklistPublicKey(study.getWorklistPublicKey());
        event.setPatientName(study.getPatientName());
        event.setVisitCode(study.getWorklistVisitCode());
        event.setAccessionNumber(study.getAccessionNumber());
        persistQuietly(event);
    }

    @Scheduled(
            initialDelayString = "${pacs.realtime.initial-delay-ms:1000}",
            fixedDelayString = "${pacs.realtime.poll-ms:1000}"
    )
    public void dispatchPendingEvents() {
        subscribersByHospital.forEach(this::dispatchHospitalEvents);
    }

    @Scheduled(cron = "${pacs.realtime.cleanup-cron:0 15 3 * * *}")
    public void cleanupOldEvents() {
        try {
            realtimeNotificationMapper.deleteEventsOlderThan(Math.max(1, eventRetentionDays));
        } catch (Exception error) {
            LOGGER.warn("Unable to clean old realtime notification events: {}", error.toString());
        }
    }

    private void dispatchHospitalEvents(Long hospitalId, CopyOnWriteArrayList<Subscriber> subscribers) {
        if (subscribers.isEmpty()) {
            subscribersByHospital.remove(hospitalId, subscribers);
            return;
        }

        long afterId = subscribers.stream().mapToLong(Subscriber::cursor).min().orElse(0L);
        try {
            List<RealtimeNotificationEvent> events =
                    realtimeNotificationMapper.listEventsAfter(hospitalId, afterId, EVENT_BATCH_SIZE);
            for (Subscriber subscriber : subscribers) {
                sendPendingEvents(hospitalId, subscriber, events);
                sendHeartbeatIfNeeded(hospitalId, subscriber);
            }
        } catch (Exception error) {
            LOGGER.warn("Unable to dispatch realtime notification events for hospital {}: {}", hospitalId, error.toString());
        }
    }

    private void sendPendingEvents(Long hospitalId, Subscriber subscriber, List<RealtimeNotificationEvent> events) {
        for (RealtimeNotificationEvent event : events) {
            if (event.getEventId() == null || event.getEventId() <= subscriber.cursor()) {
                continue;
            }
            try {
                subscriber.emitter().send(SseEmitter.event()
                        .name("notification")
                        .id(String.valueOf(event.getEventId()))
                        .data(event));
                subscriber.advance(event.getEventId());
                subscriber.markSent();
            } catch (IOException | IllegalStateException error) {
                removeSubscriber(hospitalId, subscriber);
                return;
            }
        }
    }

    private void sendHeartbeatIfNeeded(Long hospitalId, Subscriber subscriber) {
        if (Instant.now().toEpochMilli() - subscriber.lastSentAt() < 15_000L) {
            return;
        }
        try {
            subscriber.emitter().send(SseEmitter.event().comment("heartbeat"));
            subscriber.markSent();
        } catch (IOException | IllegalStateException error) {
            removeSubscriber(hospitalId, subscriber);
        }
    }

    private void trimOldUserConnections(CopyOnWriteArrayList<Subscriber> subscribers, Long userId) {
        List<Subscriber> userSubscribers = subscribers.stream()
                .filter(subscriber -> java.util.Objects.equals(userId, subscriber.userId()))
                .toList();
        int removeCount = userSubscribers.size() - MAX_CONNECTIONS_PER_USER + 1;
        for (int index = 0; index < removeCount; index++) {
            Subscriber oldSubscriber = userSubscribers.get(index);
            subscribers.remove(oldSubscriber);
            oldSubscriber.emitter().complete();
        }
    }

    private void trimOldHospitalConnections(CopyOnWriteArrayList<Subscriber> subscribers) {
        while (subscribers.size() >= MAX_CONNECTIONS_PER_HOSPITAL) {
            Subscriber oldSubscriber = subscribers.removeFirst();
            oldSubscriber.emitter().complete();
        }
    }

    private void removeSubscriber(Long hospitalId, Subscriber subscriber) {
        CopyOnWriteArrayList<Subscriber> subscribers = subscribersByHospital.get(hospitalId);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(subscriber);
        if (subscribers.isEmpty()) {
            subscribersByHospital.remove(hospitalId, subscribers);
        }
    }

    private long safeLatestEventId(Long hospitalId) {
        Long latest = realtimeNotificationMapper.findLatestEventId(hospitalId);
        return latest == null ? 0L : Math.max(0L, latest);
    }

    private RealtimeNotificationEvent baseImageReceivedEvent(Long hospitalId, Long studyId, String message) {
        RealtimeNotificationEvent event = new RealtimeNotificationEvent();
        event.setHospitalId(hospitalId);
        event.setSource("STUDY");
        event.setType("IMAGE_RECEIVED");
        event.setSeverity("success");
        event.setTitle("Images received");
        event.setMessage(firstNonBlank(message, "Images were received and the Study Archive was updated."));
        event.setStudyId(studyId);
        event.setDedupeKey("IMAGE_RECEIVED:STUDY:" + studyId);
        return event;
    }

    private void persistQuietly(RealtimeNotificationEvent event) {
        try {
            realtimeNotificationMapper.insertEvent(event);
        } catch (Exception error) {
            LOGGER.warn("Unable to persist realtime notification event {}: {}", event.getDedupeKey(), error.toString());
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static final class Subscriber {
        private final Long userId;
        private final SseEmitter emitter;
        private final AtomicLong cursor;
        private final AtomicLong lastSentAt = new AtomicLong(Instant.now().toEpochMilli());

        private Subscriber(Long userId, SseEmitter emitter, long cursor) {
            this.userId = userId;
            this.emitter = emitter;
            this.cursor = new AtomicLong(cursor);
        }

        private Long userId() {
            return userId;
        }

        private SseEmitter emitter() {
            return emitter;
        }

        private long cursor() {
            return cursor.get();
        }

        private void advance(long eventId) {
            cursor.set(eventId);
        }

        private long lastSentAt() {
            return lastSentAt.get();
        }

        private void markSent() {
            lastSentAt.set(Instant.now().toEpochMilli());
        }
    }
}
