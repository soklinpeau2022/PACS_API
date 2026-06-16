package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.dto.response.pacs.study.StudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface RealtimeNotificationService {

    SseEmitter subscribe(Long afterId);

    void publishImageReceived(WorklistDetailRow worklist, Long studyId, String studyPublicKey, String message);

    void publishImageReceived(StudyResponse study, String message);
}
