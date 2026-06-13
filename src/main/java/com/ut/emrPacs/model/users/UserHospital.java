package com.ut.emrPacs.model.users;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class UserHospital {
    @JsonIgnore
    private Long id;
    private String publicKey;
    @JsonIgnore
    private Long userId;
    private String code;
    private String abbr;
    private String name;
    private Boolean isDefault;
}
