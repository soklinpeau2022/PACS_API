package com.ut.emrPacs.model.dto.response.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class PacsResultSyncResponse {
    @JsonIgnore
    private Long worklistId;
    private String worklistPublicKey;
    private String accessionNumber;
    private String status;
    private String dicomServerStudyId;
    private String studyInstanceUid;
    private String viewerUrl;
    private String message;
}
