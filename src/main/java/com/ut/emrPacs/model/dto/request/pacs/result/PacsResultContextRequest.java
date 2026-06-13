package com.ut.emrPacs.model.dto.request.pacs.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class PacsResultContextRequest {
    private String hospitalKey;
    @JsonIgnore
    private Long hospitalId;
    private String modalityKey;
    @JsonIgnore
    private Long modalityId;
    private String worklistKey;
    @JsonIgnore
    private Long worklistId;
    private String studyKey;
    @JsonIgnore
    private Long studyId;
    private String studyInstanceUid;
    private String accessionNumber;
    private String patientCode;
}
