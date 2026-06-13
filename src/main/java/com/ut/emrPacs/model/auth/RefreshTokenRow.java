package com.ut.emrPacs.model.auth;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RefreshTokenRow {
    private Long id;
    private Long userId;
    private Long hospitalId;
    private String clientId;
    private String clientName;
    private String tokenHash;
    private Long rotatedFromId;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String revokedReason;
    private String ipAddress;
    private String userAgent;
}
