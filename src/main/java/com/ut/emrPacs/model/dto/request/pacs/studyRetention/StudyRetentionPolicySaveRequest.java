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

    @Min(value = 1, message = "Retention period must be at least 1 day.")
    @Max(value = 3650, message = "Retention period cannot exceed 3650 days.")
    private Integer retentionDays;

    @Min(value = 1, message = "Retention value must be at least 1.")
    @Max(value = 3650, message = "Retention value cannot exceed 3650.")
    private Integer retentionValue;

    @Size(max = 20)
    private String retentionUnit;

    @Min(value = 0, message = "Alert window cannot be negative.")
    @Max(value = 365, message = "Alert window cannot exceed 365 days.")
    private Integer notifyBeforeDays;

    private Boolean requireApproval;
    private Boolean enabled;
    private Boolean autoDelete;

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

    public void setRetentionUnit(String retentionUnit) {
        this.retentionUnit = trimToNull(retentionUnit);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
