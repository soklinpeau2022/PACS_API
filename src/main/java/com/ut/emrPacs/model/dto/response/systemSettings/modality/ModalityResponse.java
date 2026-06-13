package com.ut.emrPacs.model.dto.response.systemSettings.modality;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class ModalityResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    private String abbr;
    private String name;
    private Long isActive;
    @JsonIgnore
    private Long createdById;
    private String createdBy;
    private String created;
    @JsonIgnore
    private Long modifiedById;
    private String modifiedBy;
    private String modified;
}
