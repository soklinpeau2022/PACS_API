package com.ut.emrPacs.model.dto.response.systemSettings.modality;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HospitalModalityResponse {
    private List<HospitalWithModalitiesResponse> hospitals = new ArrayList<>();
}
