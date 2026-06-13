package com.ut.emrPacs.model.dto.request.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class WorklistSendToPacsRequest {
    @Schema(description = "Optional target hospital for admins managing multiple hospitals.", example = "1")
    @JsonIgnore
    private Long hospitalId;
    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    @Schema(description = "Public hospital UUID. Preferred over numeric hospitalId for frontend payloads.")
    private String hospitalKey;

    @Schema(description = "Worklist ID to send to the routed PACS / DicomServer destination.", example = "123")
    @JsonIgnore
    private Long worklistId;
    @Schema(description = "Public Worklist UUID. Preferred over numeric worklistId.")
    private String worklistKey;
    @Schema(description = "Alias of worklistKey for frontend payloads.")
    private String publicKey;
    @Schema(description = "Visit code alternative to worklistId for sending to the routed PACS / DicomServer destination.", example = "HSP001-20260511-000001")
    private String visitCode;

    @Schema(description = "Selected DICOM machine route. Required when the hospital and modality have more than one active machine route.", example = "12")
    @JsonIgnore
    private Long routeId;
    @JsonAlias({"routePublicKey", "dicomRouteKey", "dicomRoutePublicKey", "routeUuid", "routeUUID"})
    @Schema(description = "Public DICOM route UUID. Preferred over numeric routeId.")
    private String routeKey;

    @Schema(description = "Optional destination DICOM server id for legacy clients. Prefer routeId when more than one machine exists.", example = "1")
    @JsonIgnore
    private Long dicomServerId;
    @JsonAlias({"dicomServerPublicKey", "dicomServerUuid", "dicomServerUUID"})
    @Schema(description = "Public DICOM server UUID. Preferred over numeric dicomServerId.")
    private String dicomServerKey;

    @Schema(description = "Optional modality id used by route-choice lookup before a Worklist exists.", example = "2")
    @JsonIgnore
    private Long modalityId;
    @JsonAlias({"modalityPublicKey", "modalityUuid", "modalityUUID"})
    @Schema(description = "Public modality UUID used by route-choice lookup before a Worklist exists.")
    private String modalityKey;
}
