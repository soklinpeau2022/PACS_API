package com.ut.emrPacs.model.dto.response.dashboard;

import lombok.Data;

@Data
public class DashboardActionAlertResponse {
    private String code;
    private String tone;
    private String title;
    private String description;
    private Long value;
    private String path;
}
