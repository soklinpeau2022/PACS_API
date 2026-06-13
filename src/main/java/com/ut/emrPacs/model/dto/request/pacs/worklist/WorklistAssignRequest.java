package com.ut.emrPacs.model.dto.request.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class WorklistAssignRequest {
    @JsonIgnore
    private Long hospitalId;
    @NotBlank(message = "hospitalKey is required")
    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    private String hospitalKey;

    @JsonIgnore
    private Long patientId;
    @NotBlank(message = "patientKey is required")
    @JsonAlias({"patientKey", "patientPublicKey"})
    private String patientKey;
    @JsonAlias("modalityId")
    @JsonIgnore
    private Long modalityId;
    @NotBlank(message = "modalityKey is required")
    @JsonAlias({"modalityPublicKey", "modalityUuid", "modalityUUID"})
    private String modalityKey;
    @JsonIgnore
    private Long dicomServerId;
    @JsonAlias({"dicomServerPublicKey", "dicomServerUuid", "dicomServerUUID"})
    private String dicomServerKey;
    private String studyDescription;
    private LocalDate scheduledDate;
    private LocalTime scheduledTime;
    private String notes;
}
