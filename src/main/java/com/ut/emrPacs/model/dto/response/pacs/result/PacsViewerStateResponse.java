package com.ut.emrPacs.model.dto.response.pacs.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class PacsViewerStateResponse {
    @JsonIgnore
    private Long id;
    private String viewerStateKey;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalKey;
    private String hospitalCode;
    private String hospitalName;
    @JsonIgnore
    private Long modalityId;
    private String modalityKey;
    private String modalityCode;
    private String modalityName;
    @JsonIgnore
    private Long studyId;
    private String studyKey;
    @JsonIgnore
    private Long worklistId;
    private String worklistKey;
    private String worklistCode;
    @JsonIgnore
    private Long patientId;
    private String patientKey;
    private String patientCode;
    private String patientName;
    private String studyInstanceUid;
    private String accessionNumber;
    private String stateType;
    private Integer schemaVersion;
    private Integer version;
    private JsonNode viewerState;
    private JsonNode measurements;
    private JsonNode annotations;
    private JsonNode segmentations;
    private JsonNode labelmapSegmentations;
    private JsonNode contourSegmentations;
    private JsonNode surfaceSegmentations;
    private JsonNode additionalFindings;
    private JsonNode presentationState;
    private JsonNode toolState;
    private JsonNode metadata;
    private Long payloadSizeBytes;
    private String payloadSha256;
    private Boolean canEdit;
    @JsonIgnore
    private String viewerStateJson;
    @JsonIgnore
    private String measurementsJson;
    @JsonIgnore
    private String annotationsJson;
    @JsonIgnore
    private String segmentationsJson;
    @JsonIgnore
    private String labelmapSegmentationsJson;
    @JsonIgnore
    private String contourSegmentationsJson;
    @JsonIgnore
    private String surfaceSegmentationsJson;
    @JsonIgnore
    private String additionalFindingsJson;
    @JsonIgnore
    private String presentationStateJson;
    @JsonIgnore
    private String toolStateJson;
    @JsonIgnore
    private String metadataJson;
    @JsonIgnore
    private Long createdBy;
    @JsonIgnore
    private Long modifiedBy;
    private String createdAt;
    private String updatedAt;
}
