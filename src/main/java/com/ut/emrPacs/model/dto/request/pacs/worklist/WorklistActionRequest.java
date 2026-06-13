package com.ut.emrPacs.model.dto.request.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorklistActionRequest {
    @JsonIgnore
    private Long id;
    @NotBlank(message = "publicKey is required")
    private String publicKey;
    @JsonIgnore
    private Long hospitalId;
    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    private String hospitalKey;
    private String notes;
}
