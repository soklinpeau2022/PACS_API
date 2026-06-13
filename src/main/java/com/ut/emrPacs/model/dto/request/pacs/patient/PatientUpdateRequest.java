package com.ut.emrPacs.model.dto.request.pacs.patient;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ut.emrPacs.config.jackson.FlexibleLocalDateDeserializer;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientUpdateRequest {
    @JsonIgnore
    private Long hospitalId;
    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    private String hospitalKey;

    @JsonIgnore
    private Long id;
    @JsonAlias({"key", "uuid"})
    private String publicKey;
    @JsonAlias("patientUid")
    private String patientCode;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String gender;
    @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
    private LocalDate dateOfBirth;
}
