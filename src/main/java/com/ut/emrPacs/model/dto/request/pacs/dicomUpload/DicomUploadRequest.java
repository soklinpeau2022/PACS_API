package com.ut.emrPacs.model.dto.request.pacs.dicomUpload;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class DicomUploadRequest {
    @JsonIgnore
    private Long hospitalId;

    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    @Schema(description = "Hospital public key selected by the uploader.")
    private String hospitalKey;

    @JsonAlias({"dicomServerPublicKey", "dicomServerUuid", "dicomServerUUID"})
    @Schema(description = "Optional DICOM server public key. If omitted, the active hospital DICOM server is used.")
    private String dicomServerKey;
}
