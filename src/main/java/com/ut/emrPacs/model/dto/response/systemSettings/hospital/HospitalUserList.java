package com.ut.emrPacs.model.dto.response.systemSettings.hospital;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class HospitalUserList {

    @JsonIgnore
    private Long userId;
    private String publicKey;
    private String userName;
}
