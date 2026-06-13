package com.ut.emrPacs.model.dto.response.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class WorklistViewerStudyResponse {
    @JsonIgnore
    private Long worklistId;
    private String publicKey;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;
    @JsonIgnore
    private Long studyId;
    private String studyPublicKey;
    @JsonIgnore
    private Long modalityId;
    private String modalityPublicKey;
    private String patientPublicKey;
    private String visitCode;
    private String status;
    private String patientUid;
    private String patientName;
    private String accessionNumber;
    private String modalityName;
    private String studyDescription;
    private String dicomServerStudyId;
    private String studyInstanceUid;
    private String viewerUrl;
    private String viewerBaseUrl;
    private String dicomwebBaseUrl;
    private String dicomwebGatewayBaseUrl;
    private String dicomwebAuthToken;
    private String viewerApiKey;
    private String viewerAccess;
    private Boolean canEditResult;
    private Boolean canEditViewerState;
    private String dicomServerUiBaseUrl;
    private Integer totalInstances;
    private Boolean previewLimited;
    private List<WorklistViewerInstanceResponse> instances;
}
