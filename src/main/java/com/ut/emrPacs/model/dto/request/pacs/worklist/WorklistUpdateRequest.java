package com.ut.emrPacs.model.dto.request.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class WorklistUpdateRequest {
    @Schema(example = "8")
    @JsonIgnore
    private Long id;
    @NotBlank(message = "publicKey is required")
    @Schema(description = "Public Worklist UUID. Preferred over numeric id.")
    private String publicKey;

    @Schema(example = "2")
    @JsonIgnore
    private Long hospitalId;
    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    @Schema(description = "Public hospital UUID. Preferred over numeric hospitalId for frontend payloads.")
    private String hospitalKey;

    @Schema(example = "2")
    @JsonIgnore
    private Long modalityId;
    @JsonAlias({"modalityPublicKey", "modalityUuid", "modalityUUID"})
    @Schema(description = "Public modality UUID. Preferred over numeric modalityId for frontend payloads.")
    private String modalityKey;

    @Schema(example = "4")
    @JsonIgnore
    private Long dicomServerId;
    @JsonAlias({"dicomServerPublicKey", "dicomServerUuid", "dicomServerUUID"})
    @Schema(description = "Public DICOM server UUID. Preferred over numeric dicomServerId for frontend payloads.")
    private String dicomServerKey;

    @Schema(example = "CT Chest")
    private String studyDescription;

    @Schema(type = "string", format = "date", example = "2026-05-22")
    private LocalDate scheduledDate;

    @Schema(type = "string", example = "09:00")
    private LocalTime scheduledTime;

    @Schema(example = "Update from Worklist form")
    private String notes;
}
