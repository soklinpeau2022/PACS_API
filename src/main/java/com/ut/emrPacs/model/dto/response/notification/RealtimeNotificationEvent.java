package com.ut.emrPacs.model.dto.response.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class RealtimeNotificationEvent {

    private Long eventId;

    @JsonIgnore
    private Long hospitalId;

    private String source;
    private String type;
    private String severity;
    private String title;
    private String message;

    @JsonIgnore
    private Long worklistId;

    @JsonIgnore
    private Long studyId;

    private String worklistPublicKey;
    private String studyPublicKey;
    private String patientName;
    private String visitCode;
    private String accessionNumber;
    private String dedupeKey;
    private String createdAt;
}
