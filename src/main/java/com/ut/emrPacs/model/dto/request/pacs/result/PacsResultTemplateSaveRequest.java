package com.ut.emrPacs.model.dto.request.pacs.result;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PacsResultTemplateSaveRequest {
    @JsonIgnore
    @Schema(hidden = true)
    private Long id;

    @JsonAlias({"templateKey", "templatePublicKey", "key", "uuid"})
    @Schema(description = "Public template key for updates.", example = "00000000-0000-0000-0000-000000000000")
    private String publicKey;

    @JsonIgnore
    @Schema(hidden = true)
    private Long hospitalId;

    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    @Schema(description = "Public hospital key.", example = "00000000-0000-0000-0000-000000000000")
    private String hospitalKey;

    @JsonIgnore
    @Schema(hidden = true)
    private Long modalityId;

    @JsonAlias({"modalityPublicKey", "modalityUuid", "modalityUUID"})
    @Schema(description = "Public modality key.", example = "00000000-0000-0000-0000-000000000000")
    private String modalityKey;

    @Size(max = 180)
    private String templateName;

    private String templateContent;

    private Long isActive;

    private Boolean active;
}
