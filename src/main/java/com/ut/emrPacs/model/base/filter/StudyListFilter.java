package com.ut.emrPacs.model.base.filter;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ut.emrPacs.model.enums.StudyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class StudyListFilter extends Filter {
    @Schema(description = "Cursor for large-data paging: fetch studies with id below this value.")
    private Long lastStudyId;

    @Schema(description = "Cursor timestamp for large-data paging. Send with lastStudyId from the last row of the previous page.", example = "2026-06-18T10:15:30Z")
    private String lastStudySortAt;

    @Schema(description = "Filter by patient name (contains).", example = "Structured Reports")
    private String patientName;

    @Schema(description = "Filter by MRN / patient UID (contains).", example = "PID_SR")
    private String mrn;

    @Schema(description = "Filter by study date from (YYYY-MM-DD).", example = "2024-01-01")
    private String startDate;

    @Schema(description = "Filter by study date to (YYYY-MM-DD).", example = "2024-01-31")
    private String endDate;

    @Schema(description = "Filter by modality ID. Can be null.", example = "3")
    private Long modalityId;

    @JsonAlias({"modalityPublicKey", "modalityUuid", "modalityUUID"})
    @Schema(description = "Public modality key. Preferred by frontend URLs and filters.", example = "00000000-0000-0000-0000-000000000000")
    private String modalityKey;

    @Schema(description = "Filter by accession number (contains).", example = "AN_SR")
    private String accessionNumber;

    @Schema(description = "Exact accession number filter for large-data searches.", example = "CT-KSFH-260528-0001")
    private String accessionNumberExact;

    @Schema(description = "Filter by study workflow status. Allowed: IMAGE_RECEIVED, COMPLETED.", example = "IMAGE_RECEIVED")
    private String status;

    @JsonIgnore
    @Schema(hidden = true)
    private Integer statusCode;

    public void setStatus(String status) {
        this.status = status;
        try {
            this.statusCode = StudyStatus.codeOfNullable(status);
        } catch (Exception ignored) {
            this.statusCode = null;
        }
    }

    public void setPatientName(String patientName) {
        this.patientName = trimToNull(patientName);
    }

    public void setMrn(String mrn) {
        this.mrn = trimToNull(mrn);
    }

    public void setModalityKey(String modalityKey) {
        this.modalityKey = trimToNull(modalityKey);
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = trimToNull(accessionNumber);
    }

    public void setAccessionNumberExact(String accessionNumberExact) {
        this.accessionNumberExact = trimToNull(accessionNumberExact);
    }

    public void setLastStudyId(Long lastStudyId) {
        this.lastStudyId = lastStudyId != null && lastStudyId > 0L ? lastStudyId : null;
    }

    public void setLastStudySortAt(String lastStudySortAt) {
        this.lastStudySortAt = trimToNull(lastStudySortAt);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
