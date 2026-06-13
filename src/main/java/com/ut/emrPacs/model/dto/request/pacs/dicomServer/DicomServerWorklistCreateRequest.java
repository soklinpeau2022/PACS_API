package com.ut.emrPacs.model.dto.request.pacs.dicomServer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DicomServerWorklistCreateRequest {
    @JsonProperty("Tags")
    private Tags tags;

    @Data
    public static class Tags {
        @JsonProperty("PatientID")
        private String patientID;
        @JsonProperty("PatientName")
        private String patientName;
        @JsonProperty("PatientBirthDate")
        private String patientBirthDate;
        @JsonProperty("PatientSex")
        private String patientSex;
        @JsonProperty("AccessionNumber")
        private String accessionNumber;
        @JsonProperty("StudyDescription")
        private String studyDescription;
        @JsonProperty("RequestedProcedureID")
        private String requestedProcedureID;
        @JsonProperty("RequestedProcedureDescription")
        private String requestedProcedureDescription;
        @JsonProperty("ScheduledProcedureStepSequence")
        private List<ScheduledProcedureStep> scheduledProcedureStepSequence;
    }

    @Data
    public static class ScheduledProcedureStep {
        @JsonProperty("Modality")
        private String modality;
        @JsonProperty("ScheduledStationAETitle")
        private String scheduledStationAETitle;
        @JsonProperty("ScheduledProcedureStepStartDate")
        private String scheduledProcedureStepStartDate;
        @JsonProperty("ScheduledProcedureStepStartTime")
        private String scheduledProcedureStepStartTime;
        @JsonProperty("ScheduledProcedureStepDescription")
        private String scheduledProcedureStepDescription;
        @JsonProperty("ScheduledProcedureStepID")
        private String scheduledProcedureStepID;
    }
}
