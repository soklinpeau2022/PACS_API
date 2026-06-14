package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.authentication.util.AuthorityUtils;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.FunctionCodeGenerate;
import com.ut.emrPacs.helper.FunctionHelper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.hospital.HospitalMapper;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.mapper.pacs.PatientMapper;
import com.ut.emrPacs.mapper.pacs.StudyMapper;
import com.ut.emrPacs.mapper.pacs.WorklistMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.pacs.dicomUpload.DicomUploadRequest;
import com.ut.emrPacs.model.dto.request.pacs.patient.PatientCreateRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerInstanceUploadResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerSeriesResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerStudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomUpload.DicomUploadResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomUpload.DicomUploadStudySummary;
import com.ut.emrPacs.model.dto.response.pacs.patient.PatientResponse;
import com.ut.emrPacs.model.dto.response.pacs.study.StudyResponse;
import com.ut.emrPacs.model.dto.response.systemSettings.hospital.HospitalResponseDetail;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DicomServerClientService;
import com.ut.emrPacs.service.service.DicomUploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DicomUploadServiceImpl implements DicomUploadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomUploadServiceImpl.class);
    private static final int PATIENT_CODE_CREATE_RETRY = 5;
    private static final int MAX_ZIP_ENTRIES = 5000;
    private static final long MAX_ZIP_ENTRY_BYTES = 128L * 1024L * 1024L;
    private static final int ZIP_READ_BUFFER_BYTES = 64 * 1024;
    private static final int STATUS_IMAGE_RECEIVED = 1;
    private static final String UPLOAD_MODALITY_PREFIX = "UPLOAD";

    @Autowired
    private DicomServerClientService dicomServerClientService;
    @Autowired
    private DicomServerMapper dicomServerMapper;
    @Autowired
    private PatientMapper patientMapper;
    @Autowired
    private StudyMapper studyMapper;
    @Autowired
    private WorklistMapper worklistMapper;
    @Autowired
    private HospitalMapper hospitalMapper;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;
    @Autowired
    private MessageService messageService;
    @Autowired
    private ActivityLogService activityLogService;

    @Override
    public ResponseMessage<BaseResult> uploadDicom(
            DicomUploadRequest request,
            List<MultipartFile> files,
            MultipartFile zipFile,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid DICOM upload request.", false));
            }

            Long hospitalId = resolveHospitalId(publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), request.getHospitalId()));
            DicomServerResolution dicomServerResolution = resolveDicomServer(hospitalId, request.getDicomServerKey());
            if (dicomServerResolution.errorMessage() != null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(dicomServerResolution.errorMessage(), false));
            }
            HospitalDicomServerResponse dicomServer = dicomServerResolution.server();
            if (dicomServer == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("No active DICOM server found for the selected hospital.", false));
            }

            List<MultipartFile> safeFiles = files == null ? List.of() : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
            boolean hasZip = zipFile != null && !zipFile.isEmpty();
            if (safeFiles.isEmpty() && !hasZip) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Select a DICOM file, folder, or ZIP before uploading.", false));
            }
            if (!safeFiles.isEmpty() && hasZip) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Upload files/folder or one ZIP, not both at the same time.", false));
            }

            DicomUploadResponse uploadResponse = createBaseResponse(dicomServer);
            Long uploadedBy = currentUserId();
            OffsetDateTime receivedAt = OffsetDateTime.now();
            Map<String, DicomUploadStudySummary> summariesByStudyUid = new LinkedHashMap<>();
            UploadContext context = new UploadContext(
                    hospitalId,
                    dicomServer,
                    uploadedBy,
                    receivedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    uploadResponse,
                    summariesByStudyUid
            );

            if (hasZip) {
                uploadZip(zipFile, context);
            } else {
                for (MultipartFile file : safeFiles) {
                    uploadMultipartFile(file, context);
                }
            }

            uploadResponse.setStudyCount(summariesByStudyUid.size());
            uploadResponse.setStudies(new ArrayList<>(summariesByStudyUid.values()));
            uploadResponse.setViewerAvailable(uploadResponse.getStudies().stream().anyMatch(summary -> Boolean.TRUE.equals(summary.getViewerAvailable())));

            boolean success = uploadResponse.getAcceptedFiles() > 0;
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    ApiConstants.DicomUpload.BASE_PATH,
                    null,
                    success ? null : String.join("; ", uploadResponse.getErrors()),
                    "Study",
                    "Study (DICOM Upload)",
                    "Upload",
                    success ? 1 : 2,
                    success ? "Success" : "Upload failed",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );

            if (!success) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("No DICOM instances were uploaded.", List.of(uploadResponse), false));
            }
            return ResponseMessageUtils.makeResponse(true, messageService.message("DICOM upload completed.", List.of(uploadResponse), true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = error.getStackTrace() != null && error.getStackTrace().length > 0
                    ? (long) error.getStackTrace()[0].getLineNumber()
                    : null;
            LOGGER.warn("DICOM upload failed: {}", error.toString());
            activityLogService.insert(
                    ApiConstants.DicomUpload.BASE_PATH,
                    errorLine,
                    error.toString(),
                    "Study",
                    "Study (DICOM Upload)",
                    "Upload",
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to upload DICOM. Please check the selected file and DICOM server.", null, false));
        }
    }

    private void uploadMultipartFile(MultipartFile file, UploadContext context) {
        String filename = trimToNull(file.getOriginalFilename());
        try {
            DicomServerInstanceUploadResponse uploaded;
            try (InputStream inputStream = file.getInputStream()) {
                uploaded = dicomServerClientService.uploadInstance(
                        context.dicomServer.getBaseUrl(),
                        context.dicomServer.getUsername(),
                        context.dicomServer.getPassword(),
                        new InputStreamResource(inputStream),
                        file.getSize()
                );
            }
            processUploadedInstance(uploaded, context);
            context.response.setAcceptedFiles(context.response.getAcceptedFiles() + 1);
        } catch (Exception error) {
            context.response.setFailedFiles(context.response.getFailedFiles() + 1);
            addUploadError(context.response, filename, error);
        }
    }

    private void uploadZip(MultipartFile zipFile, UploadContext context) throws IOException {
        int entries = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                try {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    entries++;
                    if (entries > MAX_ZIP_ENTRIES) {
                        throw new IllegalArgumentException("ZIP contains too many files.");
                    }
                    byte[] bytes = readBoundedZipEntry(zipInputStream);
                    if (bytes.length == 0) {
                        continue;
                    }
                    if (bytes.length > MAX_ZIP_ENTRY_BYTES) {
                        throw new IllegalArgumentException("ZIP entry is too large.");
                    }
                    DicomServerInstanceUploadResponse uploaded = dicomServerClientService.uploadInstance(
                            context.dicomServer.getBaseUrl(),
                            context.dicomServer.getUsername(),
                            context.dicomServer.getPassword(),
                            new NamedByteArrayResource(bytes, entry.getName()),
                            bytes.length
                    );
                    processUploadedInstance(uploaded, context);
                    context.response.setAcceptedFiles(context.response.getAcceptedFiles() + 1);
                } catch (Exception entryError) {
                    context.response.setFailedFiles(context.response.getFailedFiles() + 1);
                    addUploadError(context.response, entry.getName(), entryError);
                } finally {
                    zipInputStream.closeEntry();
                }
            }
        }
    }

    private static byte[] readBoundedZipEntry(ZipInputStream zipInputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[ZIP_READ_BUFFER_BYTES];
        long total = 0;
        int read;
        while ((read = zipInputStream.read(buffer)) != -1) {
            total += read;
            if (total > MAX_ZIP_ENTRY_BYTES) {
                throw new IllegalArgumentException("ZIP entry is too large.");
            }
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private void processUploadedInstance(DicomServerInstanceUploadResponse upload, UploadContext context) {
        String studyId = trimToNull(upload == null ? null : upload.getParentStudy());
        if (studyId == null) {
            throw new IllegalArgumentException("DICOM server did not return a study id.");
        }
        DicomServerStudyResponse study = dicomServerClientService.getStudyById(
                context.dicomServer.getBaseUrl(),
                context.dicomServer.getUsername(),
                context.dicomServer.getPassword(),
                studyId
        );
        if (study == null) {
            throw new IllegalArgumentException("DICOM server study metadata was not available.");
        }

        Map<String, Object> studyTags = study.getMainDicomTags() == null ? Map.of() : study.getMainDicomTags();
        Map<String, Object> patientTags = study.getPatientMainDicomTags() == null ? Map.of() : study.getPatientMainDicomTags();
        String studyInstanceUid = firstNonBlank(readDicomTag(studyTags, "StudyInstanceUID"), study.getId(), studyId);
        if (studyInstanceUid == null) {
            throw new IllegalArgumentException("Uploaded DICOM has no StudyInstanceUID.");
        }

        String dicomPatientId = firstNonBlank(readDicomTag(patientTags, "PatientID"), study.getParentPatient());
        DicomPatientName patientName = splitPatientName(readDicomTag(patientTags, "PatientName"));
        LocalDate birthDate = parseDicomDate(readDicomTag(patientTags, "PatientBirthDate"));
        String gender = normalizePatientSex(readDicomTag(patientTags, "PatientSex"));
        PatientMatch patientMatch = findOrCreatePatient(context.hospitalId, dicomPatientId, patientName, birthDate, gender);
        PatientResponse patient = patientMatch.patient();

        List<DicomServerSeriesResponse> series = dicomServerClientService.getSeriesByStudyId(
                context.dicomServer.getBaseUrl(),
                context.dicomServer.getUsername(),
                context.dicomServer.getPassword(),
                studyId
        );
        String firstSeriesId = firstSeriesId(study, series, upload);
        String modality = firstNonBlank(readDicomTag(studyTags, "Modality"), firstSeriesModality(series), "OT");
        String dicomAccessionNumber = firstNonBlank(readDicomTag(studyTags, "AccessionNumber"));
        String accessionNumber = dicomAccessionNumber;
        if (accessionNumber == null) {
            accessionNumber = generateUploadAccessionNumber(context.hospitalId, modality);
        }
        String referenceVisitCode = dicomAccessionNumber;

        Long studyDbId = studyMapper.upsertFromDicomUpload(
                context.hospitalId,
                patient.getId(),
                studyInstanceUid,
                accessionNumber,
                referenceVisitCode,
                modality,
                parseDicomDate(readDicomTag(studyTags, "StudyDate")),
                firstNonBlank(readDicomTag(studyTags, "StudyDescription"), "Uploaded DICOM"),
                context.dicomServer.getId(),
                STATUS_IMAGE_RECEIVED,
                study.getId(),
                firstNonBlank(study.getParentPatient(), upload == null ? null : upload.getParentPatient()),
                firstSeriesId,
                context.uploadedBy,
                context.receivedAtIso
        );
        StudyResponse savedStudy = studyMapper.findById(context.hospitalId, studyDbId);

        DicomUploadStudySummary summary = context.summariesByStudyUid.computeIfAbsent(studyInstanceUid, ignored -> new DicomUploadStudySummary());
        summary.setStudyPublicKey(savedStudy == null ? null : savedStudy.getPublicKey());
        summary.setPatientPublicKey(patient.getPublicKey());
        summary.setPatientCode(patient.getPatientCode());
        summary.setFirstName(patient.getFirstName());
        summary.setLastName(patient.getLastName());
        summary.setPatientBirthDate(patient.getDateOfBirth() == null ? null : patient.getDateOfBirth().toString());
        summary.setPatientSex(patient.getGender());
        summary.setPatientCreated(Boolean.TRUE.equals(patientMatch.created()));
        summary.setAccessionNumber(accessionNumber);
        summary.setReferenceVisitCode(referenceVisitCode);
        summary.setStudyInstanceUid(studyInstanceUid);
        summary.setStudyDescription(savedStudy == null ? firstNonBlank(readDicomTag(studyTags, "StudyDescription"), "Uploaded DICOM") : savedStudy.getStudyDescription());
        summary.setModality(modality);
        summary.setDicomServerStudyId(study.getId());
        summary.setDicomServerPatientId(study.getParentPatient());
        summary.setDicomServerSeriesId(firstSeriesId);
        summary.setInstances(countInstances(study, series));
        summary.setStatus("IMAGE_RECEIVED");
        summary.setViewerAvailable(studyInstanceUid != null && study.getId() != null);
    }

    private PatientMatch findOrCreatePatient(
            Long hospitalId,
            String dicomPatientId,
            DicomPatientName patientName,
            LocalDate birthDate,
            String gender
    ) {
        String firstName = firstNonBlank(patientName.firstName(), dicomPatientId, "Unknown");
        String lastName = firstNonBlank(patientName.lastName(), "");
        LocalDate safeBirthDate = birthDate == null ? LocalDate.of(1900, 1, 1) : birthDate;
        String safeGender = firstNonBlank(gender, "U");

        PatientResponse existing = patientMapper.findByDemographics(hospitalId, firstName, lastName, safeBirthDate, safeGender);
        if (existing != null) {
            return new PatientMatch(existing, false);
        }

        PatientCreateRequest createRequest = new PatientCreateRequest();
        createRequest.setFirstName(firstName);
        createRequest.setLastName(lastName);
        createRequest.setGender(safeGender);
        createRequest.setDateOfBirth(safeBirthDate);
        createRequest.setPhoneNumber(null);

        for (int attempt = 0; attempt < PATIENT_CODE_CREATE_RETRY; attempt++) {
            createRequest.setPatientCode(generatePatientCode(hospitalId));
            try {
                Long patientId = patientMapper.create(hospitalId, createRequest);
                PatientResponse created = patientMapper.findById(hospitalId, patientId);
                if (created != null) {
                    return new PatientMatch(created, true);
                }
            } catch (DuplicateKeyException duplicateKeyException) {
                if (attempt == PATIENT_CODE_CREATE_RETRY - 1) {
                    throw duplicateKeyException;
                }
            }
        }
        throw new IllegalStateException("Unable to create uploaded DICOM patient.");
    }

    private String generatePatientCode(Long hospitalId) {
        String yearPrefix = FunctionCodeGenerate.currentPatientYearPrefix();
        String hospitalToken = resolveHospitalToken(hospitalId);
        long safeSequence;
        Boolean hasSequenceTable = patientMapper.existsPatientSequenceTable();
        if (Boolean.TRUE.equals(hasSequenceTable)) {
            Long nextSequence = patientMapper.nextPatientSequenceByYear(hospitalId, yearPrefix, hospitalToken);
            safeSequence = nextSequence == null || nextSequence <= 0 ? 1L : nextSequence;
        } else {
            Long lastSequence = patientMapper.maxPatientSequenceByYear(hospitalId, yearPrefix, hospitalToken);
            safeSequence = (lastSequence == null ? 0L : lastSequence) + 1L;
        }
        return FunctionCodeGenerate.buildPatientCode(yearPrefix, hospitalToken, safeSequence);
    }

    private String generateUploadAccessionNumber(Long hospitalId, String modality) {
        String dateToken = FunctionCodeGenerate.currentVisitDateToken();
        String hospitalToken = resolveHospitalToken(hospitalId);
        String sequenceKey = FunctionCodeGenerate.buildVisitSequenceKey(UPLOAD_MODALITY_PREFIX, hospitalToken, dateToken);
        Long nextSequence = worklistMapper.nextVisitSequence(hospitalId, sequenceKey);
        String shortDate = dateToken.length() >= 8 ? dateToken.substring(0, 8) : dateToken;
        return "UPLOAD-" + hospitalToken + "-" + shortDate + "-" + String.format("%04d", nextSequence == null ? 1L : Math.max(nextSequence, 1L));
    }

    private String resolveHospitalToken(Long hospitalId) {
        if (hospitalId == null || hospitalId <= 0) {
            return "HOSP";
        }
        try {
            List<HospitalResponseDetail> hospitals = hospitalMapper.getHospitalById(hospitalId);
            if (hospitals != null && !hospitals.isEmpty() && hospitals.get(0) != null) {
                HospitalResponseDetail hospital = hospitals.get(0);
                String token = FunctionHelper.normalizeHospitalToken(hospital.getAbbr());
                if (FunctionHelper.isValidHospitalToken(token)) {
                    return token;
                }
                token = FunctionHelper.normalizeHospitalToken(hospital.getCode());
                if (FunctionHelper.isValidHospitalToken(token)) {
                    return token;
                }
                token = FunctionHelper.normalizeHospitalToken(hospital.getHospitalName());
                if (FunctionHelper.hasText(token)) {
                    return token;
                }
            }
        } catch (Exception ignored) {
            return "H" + hospitalId;
        }
        return "H" + hospitalId;
    }

    private DicomServerResolution resolveDicomServer(Long hospitalId, String dicomServerKey) {
        Long dicomServerId = publicEntityKeyResolver.resolve(Entity.DICOM_SERVER, dicomServerKey, null);
        if (dicomServerId != null && dicomServerId > 0) {
            List<HospitalDicomServerResponse> rows = dicomServerMapper.getDicomServerById(dicomServerId, hospitalId);
            return new DicomServerResolution(rows == null || rows.isEmpty() ? null : rows.get(0), null);
        }
        List<HospitalDicomServerResponse> rows = dicomServerMapper.listActiveDicomServersByHospital(hospitalId);
        if (rows == null || rows.isEmpty()) {
            return new DicomServerResolution(null, null);
        }
        if (rows.size() > 1) {
            return new DicomServerResolution(null, "Select a DICOM server before uploading because this hospital has multiple active DICOM servers.");
        }
        return new DicomServerResolution(rows.get(0), null);
    }

    private DicomUploadResponse createBaseResponse(HospitalDicomServerResponse dicomServer) {
        DicomUploadResponse response = new DicomUploadResponse();
        response.setHospitalPublicKey(dicomServer.getHospitalPublicKey());
        response.setHospitalName(dicomServer.getHospitalName());
        response.setDicomServerPublicKey(dicomServer.getPublicKey());
        response.setDicomServerName(dicomServer.getName());
        return response;
    }

    private Long resolveHospitalId(Long requestedHospitalId) {
        if (requestedHospitalId != null && requestedHospitalId > 0 && AuthorityUtils.isAdminUser()) {
            return requestedHospitalId;
        }
        CurrentUserPrincipal principal = UserAuthSession.getCurrentUser();
        if (principal == null || principal.hospitalId() == null) {
            throw new IllegalStateException("Hospital context not found.");
        }
        return principal.hospitalId();
    }

    private Long currentUserId() {
        CurrentUserPrincipal principal = UserAuthSession.getCurrentUser();
        return principal == null ? null : principal.userId();
    }

    private static void addUploadError(DicomUploadResponse response, String filename, Exception error) {
        String label = trimToNull(filename) == null ? "DICOM file" : filename.trim();
        String message = error instanceof ResourceAccessException
                ? "DICOM server is unreachable."
                : firstNonBlank(error.getMessage(), "Upload failed.");
        response.getErrors().add(label + ": " + message);
    }

    private static DicomPatientName splitPatientName(String value) {
        String raw = trimToNull(value);
        if (raw == null) {
            return new DicomPatientName(null, null);
        }
        String normalized = raw.replace('^', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return new DicomPatientName(null, null);
        }
        String[] parts = normalized.split(" ", 2);
        if (parts.length == 1) {
            return new DicomPatientName(parts[0], "");
        }
        return new DicomPatientName(parts[0], parts[1]);
    }

    private static LocalDate parseDicomDate(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String digits = normalized.replaceAll("\\D", "");
        if (digits.length() != 8) {
            return null;
        }
        try {
            return LocalDate.parse(digits, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizePatientSex(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("M")) {
            return "M";
        }
        if (normalized.startsWith("F")) {
            return "F";
        }
        if (normalized.startsWith("O")) {
            return "O";
        }
        return normalized.length() > 8 ? normalized.substring(0, 8) : normalized;
    }

    private static String firstSeriesId(
            DicomServerStudyResponse study,
            List<DicomServerSeriesResponse> series,
            DicomServerInstanceUploadResponse upload
    ) {
        if (upload != null && trimToNull(upload.getParentSeries()) != null) {
            return upload.getParentSeries().trim();
        }
        if (study != null && study.getSeries() != null && !study.getSeries().isEmpty()) {
            return trimToNull(study.getSeries().get(0));
        }
        if (series != null && !series.isEmpty()) {
            return trimToNull(series.get(0).getId());
        }
        return null;
    }

    private static String firstSeriesModality(List<DicomServerSeriesResponse> series) {
        if (series == null) {
            return null;
        }
        for (DicomServerSeriesResponse item : series) {
            if (item == null || item.getMainDicomTags() == null) {
                continue;
            }
            String modality = readDicomTag(item.getMainDicomTags(), "Modality");
            if (trimToNull(modality) != null) {
                return modality;
            }
        }
        return null;
    }

    private static int countInstances(DicomServerStudyResponse study, List<DicomServerSeriesResponse> series) {
        if (study != null && study.getInstances() != null && !study.getInstances().isEmpty()) {
            return study.getInstances().size();
        }
        if (series == null) {
            return 0;
        }
        int total = 0;
        for (DicomServerSeriesResponse item : series) {
            if (item != null && item.getInstances() != null) {
                total += item.getInstances().size();
            }
        }
        return total;
    }

    private static String readDicomTag(Map<String, Object> tags, String key) {
        if (tags == null || key == null) {
            return null;
        }
        Object value = tags.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record UploadContext(
            Long hospitalId,
            HospitalDicomServerResponse dicomServer,
            Long uploadedBy,
            String receivedAtIso,
            DicomUploadResponse response,
            Map<String, DicomUploadStudySummary> summariesByStudyUid
    ) {
    }

    private record DicomServerResolution(HospitalDicomServerResponse server, String errorMessage) {
    }

    private record PatientMatch(PatientResponse patient, Boolean created) {
    }

    private record DicomPatientName(String firstName, String lastName) {
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
