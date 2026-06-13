package com.ut.emrPacs.model.dto.response.pacs.dicom;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DicomServerHealthSettingsResponse {
    private Boolean enabled;
    private Integer pollIntervalSeconds;
}
