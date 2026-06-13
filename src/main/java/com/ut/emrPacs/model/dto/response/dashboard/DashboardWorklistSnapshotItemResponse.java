package com.ut.emrPacs.model.dto.response.dashboard;

import lombok.Data;

@Data
public class DashboardWorklistSnapshotItemResponse {
    private String publicKey;
    private String visitCode;
    private String patientName;
    private String status;
}
