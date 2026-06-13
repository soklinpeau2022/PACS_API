package com.ut.emrPacs.model.dto.request.pacs.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PacsViewerStateRequest {
    private String viewerStateKey;
    @JsonIgnore
    private Long id;
    private String hospitalKey;
    @Positive
    @JsonIgnore
    private Long hospitalId;
    private String modalityKey;
    @Positive
    @JsonIgnore
    private Long modalityId;
    private String studyKey;
    @Positive
    @JsonIgnore
    private Long studyId;
    private String worklistKey;
    @Positive
    @JsonIgnore
    private Long worklistId;
    private String patientKey;
    @Positive
    @JsonIgnore
    private Long patientId;
    private String studyInstanceUid;
    private String accessionNumber;
    private String patientCode;
    private String stateType;
    private Integer schemaVersion;
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
    private Long payloadSizeBytes;
    @JsonIgnore
    private String payloadSha256;
}
