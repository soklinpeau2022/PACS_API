package com.ut.emrPacs.model.dto.request.pacs.dicom;

import lombok.Data;

@Data
public class DicomServerHealthSettingsRequest {
    private Boolean enabled;
    private Integer pollIntervalSeconds;
}
