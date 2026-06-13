package com.ut.emrPacs.model.dto.response.authentication.module;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ModuleList implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private Long moduleId;
    @JsonIgnore
    private Long moduleDetailId;
    private String modulePublicKey;
    private String moduleDetailPublicKey;
}
