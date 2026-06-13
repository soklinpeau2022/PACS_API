package com.ut.emrPacs.model.dto.response.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class NotificationResponse {

    private String id;

    @JsonIgnore
    private Long referenceId;

    private String source;

    private String type;

    private String severity;

    private String title;

    private String message;

    private String module;

    private String action;

    private String username;

    private String userName;

    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;

    private String hospitalName;

    @JsonIgnore
    private Long patientId;
    private String patientPublicKey;

    private String patientName;

    private String patientUid;

    @JsonIgnore
    private Long worklistId;
    private String worklistPublicKey;

    @JsonIgnore
    private Long studyId;
    private String studyPublicKey;

    private String visitCode;

    private String accessionNumber;

    private String modalityName;

    private String createdAt;

    private String endpoint;

    private Boolean unread;
}
