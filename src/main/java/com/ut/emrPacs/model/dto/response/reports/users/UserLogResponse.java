package com.ut.emrPacs.model.dto.response.reports.users;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class UserLogResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    @JsonIgnore
    private Long userId;
    private String dateTime;
    private String userName;
    private String username;
    private String action;
    private String actionName;
    private String createdAt;
    private String description;
    private String message;
    private String remoteAddr;
    private String httpUserAgent;
}
