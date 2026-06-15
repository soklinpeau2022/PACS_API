package com.ut.emrPacs.model.dto.response.pacs.worklist;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistResponse;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class WorklistDicomWorklistResponse {
    @JsonIgnore
    private Long worklistId;
    private String worklistPublicKey;
    private String visitCode;
    private String status;
    private String patientUid;
    private String patientHn;
    private String patientName;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate patientBirthDate;
    private String patientSex;
    private String accessionNumber;
    private String dicomServerWorklistId;
    private String dicomServerWorklistPath;
    private String modalityCode;
    private String studyDescription;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate scheduledDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    private LocalTime scheduledTime;
    private String scheduledStationAeTitle;
    private String message;
    private DicomServerWorklistResponse worklist;
}
