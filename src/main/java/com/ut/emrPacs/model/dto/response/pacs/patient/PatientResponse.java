package com.ut.emrPacs.model.dto.response.pacs.patient;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;
    private String hospitalCode;
    private String hospitalName;
    private String patientCode;
    private String patientHn;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String gender;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;
}
