package com.ut.emrPacs.model.base.filter;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PatientListFilter extends Filter {
    /**
     * Cursor pagination support for very large patient tables.
     * When provided, list will fetch rows with id < lastPatientId and avoid deep OFFSET scans.
     */
    @Schema(description = "Cursor for big-data paging: fetch rows with id < lastPatientId (recommended for deep paging).")
    private Long lastPatientId;

    @Schema(description = "Exact patient code / MRN filter for large-data searches.", example = "2026-KSFH-P0000001")
    private String patientCode;

    @Schema(description = "Patient first name contains filter.", example = "Sok")
    private String firstName;

    @Schema(description = "Patient last name contains filter.", example = "Lin")
    private String lastName;

    @Schema(description = "Exact phone number filter.", example = "0812345678")
    private String phoneNumber;

    @Schema(description = "Gender filter.", example = "M")
    private String gender;

    public void setPatientCode(String patientCode) {
        this.patientCode = trimToNull(patientCode);
    }

    public void setFirstName(String firstName) {
        this.firstName = trimToNull(firstName);
    }

    public void setLastName(String lastName) {
        this.lastName = trimToNull(lastName);
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = trimToNull(phoneNumber);
    }

    public void setGender(String gender) {
        this.gender = trimToNull(gender);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
