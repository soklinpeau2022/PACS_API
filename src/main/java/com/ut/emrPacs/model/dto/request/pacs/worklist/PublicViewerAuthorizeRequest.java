package com.ut.emrPacs.model.dto.request.pacs.worklist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicViewerAuthorizeRequest {
    @NotBlank
    @Size(max = 36)
    private String hospitalKey;

    @NotBlank
    @Size(max = 36)
    private String worklistKey;

    @NotBlank
    @Size(min = 7, max = 32)
    private String phoneNumber;

    @Size(max = 32)
    private String mode;
}
