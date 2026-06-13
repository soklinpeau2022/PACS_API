package com.ut.emrPacs.model.dto.request.systemSettings.hospital;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class HospitalModalityRequest {
    @JsonIgnore
    private Long userId;
    @JsonAlias({"userPublicKey", "userUuid", "publicKey", "key"})
    private String userKey;
}
