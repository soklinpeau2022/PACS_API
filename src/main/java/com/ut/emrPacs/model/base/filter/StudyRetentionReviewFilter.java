package com.ut.emrPacs.model.base.filter;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Locale;

@EqualsAndHashCode(callSuper = true)
@Data
public class StudyRetentionReviewFilter extends Filter {
    @JsonIgnore
    @Schema(hidden = true)
    private Long dicomServerId;

    @JsonAlias({"dicomServerPublicKey", "dicomServerUuid", "dicomServerUUID"})
    private String dicomServerKey;

    @JsonIgnore
    @Schema(hidden = true)
    private Long modalityId;

    @JsonAlias({"modalityPublicKey", "modalityUuid", "modalityUUID"})
    private String modalityKey;

    private String status;
    private String startDate;
    private String endDate;
    private String lastStatus;
    private String lastExpiresAt;
    private Long lastStudyId;

    public void setDicomServerKey(String dicomServerKey) {
        this.dicomServerKey = trimToNull(dicomServerKey);
    }

    public void setModalityKey(String modalityKey) {
        this.modalityKey = trimToNull(modalityKey);
    }

    public void setStatus(String status) {
        String value = trimToNull(status);
        this.status = value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    public void setStartDate(String startDate) {
        this.startDate = trimToNull(startDate);
    }

    public void setEndDate(String endDate) {
        this.endDate = trimToNull(endDate);
    }

    public void setLastStatus(String lastStatus) {
        String value = trimToNull(lastStatus);
        this.lastStatus = value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    public void setLastExpiresAt(String lastExpiresAt) {
        this.lastExpiresAt = trimToNull(lastExpiresAt);
    }

    public void setLastStudyId(Long lastStudyId) {
        this.lastStudyId = lastStudyId != null && lastStudyId > 0 ? lastStudyId : null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
