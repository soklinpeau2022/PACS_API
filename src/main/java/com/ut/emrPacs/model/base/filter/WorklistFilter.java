package com.ut.emrPacs.model.base.filter;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ut.emrPacs.model.enums.WorklistStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class WorklistFilter extends Filter {
    private static final List<String> DEFAULT_WORKLIST_STATUSES = List.of(
            WorklistStatus.WAITING.name(),
            WorklistStatus.IN_PROGRESS.name(),
            WorklistStatus.CANCELLED.name(),
            WorklistStatus.FAILED.name()
    );

    private Long hospitalId;
    @JsonAlias("modalityId")
    private Long modalityId;
    @JsonIgnore
    @Schema(hidden = true)
    private Long lastWorklistId;
    @Schema(description = "Cursor timestamp for big-data paging. Send with lastWorklistId from the last row of the previous page.", example = "2026-06-18T10:15:30Z")
    private String lastCreatedAt;
    private String status;
    @JsonIgnore
    @Schema(hidden = true)
    private Integer statusCode;
    @Schema(description = "Optional multi-status filter. Defaults to operational Worklist statuses when empty: WAITING, IN_PROGRESS, CANCELLED, FAILED. Rows with received image/study data are excluded from the active Worklist list and should be read from Studies.")
    private List<String> statuses;
    @JsonIgnore
    @Schema(hidden = true)
    private List<Integer> statusCodes;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    @Schema(description = "When true, only WAITING worklists older than overdueMinutes are returned.")
    private Boolean overdueOnly;
    @Schema(description = "Age threshold used with overdueOnly.", example = "30")
    private Integer overdueMinutes;
    @Schema(description = "Exact patient code / MRN filter for large-data searches.", example = "2026-KSFH-P0000001")
    private String patientCode;
    @Schema(description = "Patient name contains filter.", example = "Sok")
    private String patientName;
    @Schema(description = "Exact phone number filter.", example = "0812345678")
    private String phoneNumber;
    @Schema(description = "Exact Worklist/visit number filter.", example = "Q-20260528-0001")
    private String worklistNumber;
    @Schema(description = "Exact visit code filter. worklistNumber is accepted as an alias by the API.", example = "Q-20260528-0001")
    private String visitCode;
    @Schema(description = "Exact accession number filter.", example = "CT-KSFH-260528-0001")
    private String accessionNumber;

    public void setStatus(String status) {
        this.status = status;
        try {
            this.statusCode = WorklistStatus.codeOfNullable(status);
        } catch (Exception ignored) {
            this.statusCode = null;
        }
    }

    public void setStatuses(List<String> statuses) {
        this.statuses = statuses;
        this.statusCodes = parseStatusCodes(statuses);
    }

    public void applyOperationalWorklistDefaultStatuses() {
        if (statusCode != null) {
            return;
        }
        if (statusCodes != null && !statusCodes.isEmpty()) {
            return;
        }
        setStatuses(DEFAULT_WORKLIST_STATUSES);
    }

    private List<Integer> parseStatusCodes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        List<Integer> parsed = new ArrayList<>();
        for (String value : values) {
            try {
                Integer status = WorklistStatus.codeOfNullable(value);
                if (status != null && !parsed.contains(status)) {
                    parsed.add(status);
                }
            } catch (Exception ignored) {
                // Ignore unknown values so one stale UI option does not break the whole list request.
            }
        }
        return parsed.isEmpty() ? null : parsed;
    }

    public void setPatientCode(String patientCode) {
        this.patientCode = trimToNull(patientCode);
    }

    public void setPatientName(String patientName) {
        this.patientName = trimToNull(patientName);
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = trimToNull(phoneNumber);
    }

    public void setWorklistNumber(String worklistNumber) {
        this.worklistNumber = trimToNull(worklistNumber);
    }

    public void setVisitCode(String visitCode) {
        this.visitCode = trimToNull(visitCode);
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = trimToNull(accessionNumber);
    }

    public void setLastWorklistId(Long lastWorklistId) {
        this.lastWorklistId = lastWorklistId != null && lastWorklistId > 0L ? lastWorklistId : null;
    }

    public void setLastCreatedAt(String lastCreatedAt) {
        this.lastCreatedAt = trimToNull(lastCreatedAt);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
