package com.ut.emrPacs.model.dto.request.pacs.worklist;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorklistFindRequest {
    @JsonIgnore
    private Long hospitalId;
    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    private String hospitalKey;

    @Schema(example = "8")
    @JsonIgnore
    private Long id;
    @NotBlank(message = "publicKey is required")
    @Schema(description = "Public Worklist UUID. Preferred over numeric id.")
    private String publicKey;
}
