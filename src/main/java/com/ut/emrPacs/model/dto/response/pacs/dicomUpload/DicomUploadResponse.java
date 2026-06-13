package com.ut.emrPacs.model.dto.response.pacs.dicomUpload;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DicomUploadResponse {
    private String hospitalPublicKey;
    private String hospitalName;
    private String dicomServerPublicKey;
    private String dicomServerName;
    private Integer acceptedFiles = 0;
    private Integer failedFiles = 0;
    private Integer studyCount = 0;
    private String status = "IMAGE_RECEIVED";
    private Boolean viewerAvailable = false;
    private List<DicomUploadStudySummary> studies = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
}
