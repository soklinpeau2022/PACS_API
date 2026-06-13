package com.ut.emrPacs.model.dto.response.systemSettings.modality;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class HospitalModalityFlatRow {
    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;
    private String hospitalName;
    @JsonIgnore
    private Long modalityId;
    private String modalityPublicKey;
    private String modalityName;
}
