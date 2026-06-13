package com.ut.emrPacs.helper.dicomServer;

import com.ut.emrPacs.helper.FunctionHelper;
import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerWorklistCreateRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDicomWorklistResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public final class DicomServerWorklistMapperHelper {

    private static final DateTimeFormatter DICOM_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DICOM_TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final String UNKNOWN_DICOM_BIRTH_DATE = "19000101";

    private DicomServerWorklistMapperHelper() {
    }

    public static DicomServerWorklistCreateRequest toCreateRequest(
            WorklistDetailRow Worklist,
            String accessionNumber,
            String modalityCode,
            String studyDescription,
            LocalDate scheduledDate,
            LocalTime scheduledTime,
            String scheduledStationAeTitle
    ) {
        DicomServerWorklistCreateRequest.ScheduledProcedureStep step = new DicomServerWorklistCreateRequest.ScheduledProcedureStep();
        step.setModality(firstNonBlank(modalityCode, "OT"));
        step.setScheduledStationAETitle(firstNonBlank(scheduledStationAeTitle, "UDAYA_DICOM_SERVER"));
        step.setScheduledProcedureStepStartDate(formatDicomDate(scheduledDate));
        step.setScheduledProcedureStepStartTime(formatDicomTime(scheduledTime));
        step.setScheduledProcedureStepDescription(studyDescription);
        step.setScheduledProcedureStepID(accessionNumber);

        DicomServerWorklistCreateRequest.Tags tags = new DicomServerWorklistCreateRequest.Tags();
        tags.setPatientID(firstNonBlank(Worklist == null ? null : Worklist.getPatientUid(), accessionNumber, "UNKNOWN"));
        tags.setPatientName(firstNonBlank(Worklist == null ? null : Worklist.getPatientName(), "UNKNOWN"));
        String birthDate = formatDicomDate(Worklist == null ? null : Worklist.getDob());
        tags.setPatientBirthDate(hasText(birthDate) ? birthDate : UNKNOWN_DICOM_BIRTH_DATE);
        tags.setPatientSex(normalizeSex(Worklist == null ? null : Worklist.getSex()));
        tags.setAccessionNumber(accessionNumber);
        tags.setStudyDescription(studyDescription);
        tags.setRequestedProcedureID(accessionNumber);
        tags.setRequestedProcedureDescription(studyDescription);
        tags.setScheduledProcedureStepSequence(List.of(step));

        DicomServerWorklistCreateRequest request = new DicomServerWorklistCreateRequest();
        request.setTags(tags);
        return request;
    }

    public static WorklistDicomWorklistResponse toWorklistDicomWorklistResponse(
            WorklistDetailRow Worklist,
            DicomServerWorklistResponse worklist,
            String message
    ) {
        WorklistDicomWorklistResponse response = new WorklistDicomWorklistResponse();
        if (Worklist != null) {
            response.setWorklistId(Worklist.getId());
            response.setWorklistPublicKey(Worklist.getPublicKey());
            response.setVisitCode(Worklist.getVisitCode());
            response.setStatus(Worklist.getStatus());
            response.setPatientUid(Worklist.getPatientUid());
            response.setPatientName(Worklist.getPatientName());
            response.setPatientBirthDate(Worklist.getDob());
            response.setPatientSex(Worklist.getSex());
            response.setAccessionNumber(Worklist.getAccessionNumber());
            response.setDicomServerWorklistId(Worklist.getDicomServerWorklistId());
            response.setDicomServerWorklistPath(Worklist.getDicomServerWorklistPath());
            response.setModalityCode(Worklist.getModalityCode());
            response.setStudyDescription(Worklist.getStudyDescription());
            response.setScheduledDate(Worklist.getScheduledDate());
            response.setScheduledTime(Worklist.getScheduledTime());
            response.setScheduledStationAeTitle(Worklist.getMachineAeTitle());
        }
        if (worklist != null) {
            response.setWorklist(worklist);
            if (hasText(worklist.getId())) {
                response.setDicomServerWorklistId(worklist.getId().trim());
            }
            if (hasText(worklist.getPath())) {
                response.setDicomServerWorklistPath(worklist.getPath().trim());
            }
            DicomServerWorklistResponse.Tags tags = worklist.getTags();
            if (tags != null) {
                response.setPatientUid(firstNonBlank(tags.getPatientID(), response.getPatientUid()));
                response.setPatientName(firstNonBlank(tags.getPatientName(), response.getPatientName()));
                response.setPatientSex(firstNonBlank(tags.getPatientSex(), response.getPatientSex()));
                response.setAccessionNumber(firstNonBlank(tags.getAccessionNumber(), response.getAccessionNumber()));
                response.setStudyDescription(firstNonBlank(
                        tags.getStudyDescription(),
                        tags.getRequestedProcedureDescription(),
                        response.getStudyDescription()
                ));
                response.setPatientBirthDate(
                        coalesceDate(parseDicomDate(tags.getPatientBirthDate()), response.getPatientBirthDate())
                );
                DicomServerWorklistResponse.ScheduledProcedureStep step = firstStep(tags.getScheduledProcedureStepSequence());
                if (step != null) {
                    response.setModalityCode(firstNonBlank(step.getModality(), response.getModalityCode()));
                    response.setScheduledStationAeTitle(firstNonBlank(step.getScheduledStationAETitle(), response.getScheduledStationAeTitle()));
                    response.setStudyDescription(firstNonBlank(step.getScheduledProcedureStepDescription(), response.getStudyDescription()));
                    response.setScheduledDate(coalesceDate(parseDicomDate(step.getScheduledProcedureStepStartDate()), response.getScheduledDate()));
                    response.setScheduledTime(coalesceTime(parseDicomTime(step.getScheduledProcedureStepStartTime()), response.getScheduledTime()));
                }
            }
        }
        response.setMessage(message);
        return response;
    }

    public static SyncedWorklistFields toSyncedWorklistFields(
            DicomServerWorklistResponse worklist,
            WorklistDetailRow Worklist,
            String fallbackAeTitle
    ) {
        WorklistDicomWorklistResponse mapped = toWorklistDicomWorklistResponse(Worklist, worklist, null);
        SyncedWorklistFields fields = new SyncedWorklistFields();
        fields.setDicomServerWorklistId(mapped.getDicomServerWorklistId());
        fields.setDicomServerWorklistPath(mapped.getDicomServerWorklistPath());
        fields.setAccessionNumber(mapped.getAccessionNumber());
        fields.setModalityCode(firstNonBlank(mapped.getModalityCode(), Worklist == null ? null : Worklist.getModalityCode(), "OT"));
        fields.setStudyDescription(firstNonBlank(mapped.getStudyDescription(), Worklist == null ? null : Worklist.getStudyDescription(), "PACS Study"));
        fields.setScheduledDate(coalesceDate(mapped.getScheduledDate(), Worklist == null ? null : Worklist.getScheduledDate()));
        fields.setScheduledTime(coalesceTime(mapped.getScheduledTime(), Worklist == null ? null : Worklist.getScheduledTime()));
        fields.setScheduledStationAeTitle(firstNonBlank(mapped.getScheduledStationAeTitle(), fallbackAeTitle, "UDAYA_DICOM_SERVER"));
        return fields;
    }

    public static LocalDate parseDicomDate(String rawDate) {
        if (!hasText(rawDate)) {
            return null;
        }
        String normalized = rawDate.trim().replace("-", "");
        if (normalized.length() != 8) {
            return null;
        }
        try {
            return LocalDate.parse(normalized, DICOM_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static LocalTime parseDicomTime(String rawTime) {
        if (!hasText(rawTime)) {
            return null;
        }
        String digits = rawTime.trim().replace(":", "");
        if (digits.length() < 2) {
            return null;
        }
        String normalized = switch (digits.length()) {
            case 2 -> digits + "0000";
            case 4 -> digits + "00";
            default -> digits.substring(0, Math.min(6, digits.length()));
        };
        while (normalized.length() < 6) {
            normalized = normalized + "0";
        }
        try {
            return LocalTime.parse(normalized, DICOM_TIME);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static String formatDicomDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DICOM_DATE);
    }

    public static String formatDicomTime(LocalTime time) {
        if (time == null) {
            return "000000";
        }
        return time.withNano(0).format(DICOM_TIME);
    }

    public static String normalizeSex(String rawSex) {
        String normalized = rawSex == null ? "" : rawSex.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("M")) {
            return "M";
        }
        if (normalized.startsWith("F")) {
            return "F";
        }
        return "O";
    }

    public static String normalizeModality(String rawModality) {
        String normalized = rawModality == null ? "" : rawModality.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("CT")) return "CT";
        if (normalized.equals("MR") || normalized.equals("MRI")) return "MR";
        if (normalized.equals("US")) return "US";
        if (normalized.equals("CR")) return "CR";
        if (normalized.equals("DR")) return "DX";
        if (normalized.equals("DX")) return "DX";
        if (normalized.equals("MG")) return "MG";
        if (normalized.equals("NM")) return "NM";
        if (normalized.equals("PT") || normalized.equals("PET")) return "PT";
        if (normalized.equals("XA")) return "XA";
        if (normalized.equals("OT")) return "OT";
        if (normalized.contains("COMPUTED TOMOGRAPHY")) return "CT";
        if (normalized.contains("MAGNETIC RESONANCE")) return "MR";
        if (normalized.contains("POSITRON EMISSION TOMOGRAPHY")) return "PT";
        if (normalized.contains("NUCLEAR MEDICINE")) return "NM";
        if (normalized.contains("CT")) return "CT";
        if (normalized.contains("MRI") || normalized.equals("MR")) return "MR";
        if (normalized.contains("ECHO") || normalized.contains("US") || normalized.contains("ULTRASOUND")) return "US";
        if (normalized.contains("DIGITAL RADIOGRAPHY")) return "DX";
        if (normalized.contains("COMPUTED RADIOGRAPHY")) return "CR";
        if (normalized.contains("DIAGNOSTIC X-RAY") || normalized.contains("DIAGNOSTIC XRAY")) return "DX";
        if (normalized.contains("ANGIO")) return "XA";
        if (normalized.contains("XRAY") || normalized.equals("X-RAY") || normalized.equals("XR")) return "CR";
        if (normalized.contains("DX")) return "DX";
        if (normalized.contains("MG")) return "MG";
        if (normalized.contains("NM")) return "NM";
        if (normalized.contains("PET") || normalized.equals("PT")) return "PT";
        return "OT";
    }

    public static String defaultStudyDescription(String serviceName, String modalityName) {
        String service = firstNonBlank(serviceName, "");
        String modality = firstNonBlank(modalityName, "");
        if (!service.isEmpty() && !modality.isEmpty()) {
            return service + " - " + modality;
        }
        if (!service.isEmpty()) {
            return service;
        }
        if (!modality.isEmpty()) {
            return modality;
        }
        return "PACS Study";
    }

    private static DicomServerWorklistResponse.ScheduledProcedureStep firstStep(List<DicomServerWorklistResponse.ScheduledProcedureStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        return steps.get(0);
    }

    private static LocalDate coalesceDate(LocalDate preferred, LocalDate fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static LocalTime coalesceTime(LocalTime preferred, LocalTime fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static boolean hasText(String value) {
        return FunctionHelper.hasText(value);
    }

    private static String firstNonBlank(String... values) {
        return FunctionHelper.firstNonBlank(values);
    }

    public static final class SyncedWorklistFields {
        private String dicomServerWorklistId;
        private String dicomServerWorklistPath;
        private String accessionNumber;
        private String modalityCode;
        private String studyDescription;
        private LocalDate scheduledDate;
        private LocalTime scheduledTime;
        private String scheduledStationAeTitle;

        public String getDicomServerWorklistId() {
            return dicomServerWorklistId;
        }

        public void setDicomServerWorklistId(String dicomServerWorklistId) {
            this.dicomServerWorklistId = dicomServerWorklistId;
        }

        public String getDicomServerWorklistPath() {
            return dicomServerWorklistPath;
        }

        public void setDicomServerWorklistPath(String dicomServerWorklistPath) {
            this.dicomServerWorklistPath = dicomServerWorklistPath;
        }

        public String getAccessionNumber() {
            return accessionNumber;
        }

        public void setAccessionNumber(String accessionNumber) {
            this.accessionNumber = accessionNumber;
        }

        public String getModalityCode() {
            return modalityCode;
        }

        public void setModalityCode(String modalityCode) {
            this.modalityCode = modalityCode;
        }

        public String getStudyDescription() {
            return studyDescription;
        }

        public void setStudyDescription(String studyDescription) {
            this.studyDescription = studyDescription;
        }

        public LocalDate getScheduledDate() {
            return scheduledDate;
        }

        public void setScheduledDate(LocalDate scheduledDate) {
            this.scheduledDate = scheduledDate;
        }

        public LocalTime getScheduledTime() {
            return scheduledTime;
        }

        public void setScheduledTime(LocalTime scheduledTime) {
            this.scheduledTime = scheduledTime;
        }

        public String getScheduledStationAeTitle() {
            return scheduledStationAeTitle;
        }

        public void setScheduledStationAeTitle(String scheduledStationAeTitle) {
            this.scheduledStationAeTitle = scheduledStationAeTitle;
        }
    }
}
