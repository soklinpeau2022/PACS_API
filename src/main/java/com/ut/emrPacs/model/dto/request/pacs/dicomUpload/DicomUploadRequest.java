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
    @Schema(description = "DICOM server public key. Required when the selected hospital has multiple active DICOM servers.")
    private String dicomServerKey;
}
