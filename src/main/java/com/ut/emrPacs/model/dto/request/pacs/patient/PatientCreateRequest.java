package com.ut.emrPacs.model.dto.request.pacs.patient;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ut.emrPacs.config.jackson.FlexibleLocalDateDeserializer;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientCreateRequest {
    @JsonIgnore
    private Long hospitalId;
    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    private String hospitalKey;

    @JsonAlias("patientUid")
    private String patientCode;
    @JsonAlias({"patientHN", "patientHn", "dicomPatientId", "dicomPatientID"})
    private String patientHn;

    @NotBlank
    private String firstName;

    private String lastName;

    private String phoneNumber;
    private String gender;
    @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
    private LocalDate dateOfBirth;
    private Boolean createWorklistIntent;
}
