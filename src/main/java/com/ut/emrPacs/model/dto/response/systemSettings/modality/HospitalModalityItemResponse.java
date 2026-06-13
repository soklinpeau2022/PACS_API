package com.ut.emrPacs.model.dto.response.systemSettings.modality;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class HospitalModalityItemResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    private String name;
}
