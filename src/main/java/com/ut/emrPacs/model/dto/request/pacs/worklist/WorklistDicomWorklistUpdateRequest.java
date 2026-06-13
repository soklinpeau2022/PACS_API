package com.ut.emrPacs.model.dto.request.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class WorklistDicomWorklistUpdateRequest {
    @JsonIgnore
    private Long modalityId;
    @JsonAlias({"modalityPublicKey", "modalityUuid", "modalityUUID"})
    private String modalityKey;
    private String studyDescription;
    private LocalDate scheduledDate;
    private LocalTime scheduledTime;
}
