package com.ut.emrPacs.model.dto.response.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class ViewerInfoResponse {
    private Boolean success;
    private Boolean directDicomweb;
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
    private String worklistStatus;
    private String status;
    private String dicomServerStudyId;
    private String dicomServerPatientId;
    private String dicomServerSeriesId;
    private String studyInstanceUid;
    private String accessionNumber;
    private String patientUid;
    private String patientName;
    private String modalityName;
    private String studyDescription;
    private String imageReceivedAt;
    private Integer imageInstanceCount;
    private Integer totalInstances;
    private Integer seriesCount;
    private String viewerBaseUrl;
    private String dicomwebBaseUrl;
    private String dicomwebGatewayBaseUrl;
    private String dicomwebAuthToken;
    private String viewerApiKey;
    private String viewerAccess;
    private Boolean canEditResult;
    private Boolean canEditViewerState;
    private String dicomServerUiBaseUrl;
    private String viewerUrl;
    private String basicViewerUrl;
    private String segmentationViewerUrl;
    private String publicViewerUrl;
}
