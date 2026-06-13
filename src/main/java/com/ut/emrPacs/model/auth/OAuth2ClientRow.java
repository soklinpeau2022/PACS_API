package com.ut.emrPacs.model.auth;

import lombok.Data;

@Data
public class OAuth2ClientRow {
    private Long id;
    private String clientId;
    private String clientName;
    private String clientSecretHash;
    private String clientType;
    private String allowedGrantTypes;
    private String allowedScopes;
    private Long accessTokenLifetimeMs;
    private Long refreshTokenLifetimeMs;
    private Long dicomServerId;
    private Boolean isActive;

    public boolean isConfidential() {
        return "CONFIDENTIAL".equalsIgnoreCase(clientType);
    }

    public boolean allowsGrantType(String grantType) {
        if (grantType == null || allowedGrantTypes == null) return false;
        for (String g : allowedGrantTypes.split(",")) {
            if (grantType.trim().equalsIgnoreCase(g.trim())) return true;
        }
        return false;
    }

}
