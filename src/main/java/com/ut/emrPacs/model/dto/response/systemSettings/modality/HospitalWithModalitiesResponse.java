package com.ut.emrPacs.model.dto.response.systemSettings.modality;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HospitalWithModalitiesResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    private String name;
    private List<HospitalModalityItemResponse> modalities = new ArrayList<>();

    @JsonProperty("modalities")
    public List<HospitalModalityItemResponse> getModalities() {
        return modalities;
    }
}
