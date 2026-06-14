package com.ut.emrPacs.model.dto.request.pacs.studyRetention;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudyRetentionPolicySaveRequest {
    @JsonIgnore
    private Long id;

    @JsonAlias({"key", "uuid"})
    private String publicKey;

    @JsonIgnore
    private Long hospitalId;

    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    private String hospitalKey;

    @JsonIgnore
    private Long dicomServerId;

    @JsonAlias({"dicomServerPublicKey", "dicomServerUuid", "dicomServerUUID"})
    private String dicomServerKey;

    @JsonIgnore
    private Long modalityId;

    @JsonAlias({"modalityPublicKey", "modalityUuid", "modalityUUID"})
    private String modalityKey;

    @Min(1)
    @Max(3650)
    private Integer retentionDays;

    @Min(0)
    @Max(365)
    private Integer notifyBeforeDays;

    private Boolean requireApproval;
    private Boolean enabled;

    @Size(max = 1000)
    private String notes;

    public void setPublicKey(String publicKey) {
        this.publicKey = trimToNull(publicKey);
    }

    public void setHospitalKey(String hospitalKey) {
        this.hospitalKey = trimToNull(hospitalKey);
    }

    public void setDicomServerKey(String dicomServerKey) {
        this.dicomServerKey = trimToNull(dicomServerKey);
    }

    public void setModalityKey(String modalityKey) {
        this.modalityKey = trimToNull(modalityKey);
    }

    public void setNotes(String notes) {
        this.notes = trimToNull(notes);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
