package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.authentication.util.AuthorityUtils;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.config.WorklistConstants;
import com.ut.emrPacs.helper.FunctionCodeGenerate;
import com.ut.emrPacs.helper.FunctionHelper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.hospital.HospitalMapper;
import com.ut.emrPacs.mapper.modality.ModalityMapper;
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
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistAssignRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerInstanceUploadResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerSeriesResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerStudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomUpload.DicomUploadResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomUpload.DicomUploadStudySummary;
import com.ut.emrPacs.model.dto.response.pacs.patient.PatientResponse;
import com.ut.emrPacs.model.dto.response.pacs.study.StudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import com.ut.emrPacs.model.enums.WorklistStatus;
import com.ut.emrPacs.model.dto.response.systemSettings.hospital.HospitalResponseDetail;
import com.ut.emrPacs.model.dto.response.systemSettings.modality.ModalityResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DicomServerClientService;
import com.ut.emrPacs.service.service.DicomUploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.ut.emrPacs.helper.dicomServer.DicomResponseReadHelper.readDicomServerInstanceCount;

@Service
public class DicomUploadServiceImpl implements DicomUploadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomUploadServiceImpl.class);
    private static final int PATIENT_CODE_CREATE_RETRY = 5;
    private static final int MAX_ZIP_ENTRIES = 5000;
    private static final long DEFAULT_MAX_DICOM_UPLOAD_BYTES = 4L * 1024L * 1024L * 1024L;
    private static final int ZIP_READ_BUFFER_BYTES = 64 * 1024;
    private static final int STATUS_IMAGE_RECEIVED = 1;

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
    private ModalityMapper modalityMapper;
    @Autowired
    private HospitalMapper hospitalMapper;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;
    @Autowired
    private MessageService messageService;
    @Autowired
    private ActivityLogService activityLogService;
    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    @Value("${spring.servlet.multipart.location:${HOSPITAL_IMAGE_ROOT_PATH:/var/ut-image}/tmp/dicom-upload}")
    private String multipartLocation;

    @Value("${pacs.dicom-upload.temp-dir:${java.io.tmpdir}/udaya-pacs-dicom-upload}")
    private String dicomUploadTempDir;

    @Value("${app.security.dicom-upload.max-request-bytes:4294967296}")
    private long maxDicomUploadRequestBytes;

    @Value("${pacs.dicom-upload.max-zip-entry-bytes:4294967296}")
    private long maxZipEntryBytes;

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
            long requestBytes = hasZip
                    ? zipFile.getSize()
                    : safeFiles.stream().mapToLong(MultipartFile::getSize).sum();
            long maxRequestBytes = configuredMaxUploadBytes();
            if (requestBytes > maxRequestBytes) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("DICOM upload can be up to " + formatBytes(maxRequestBytes) + " per request.", false));
            }

            DicomUploadResponse uploadResponse = createBaseResponse(dicomServer);
            Long uploadedBy = currentUserId();
            OffsetDateTime receivedAt = OffsetDateTime.now();
            Map<String, DicomUploadStudySummary> summariesByStudyUid = new LinkedHashMap<>();
            Map<String, UploadWorklistContext> worklistsByStudyUid = new LinkedHashMap<>();
            Set<Long> receivedWorklistIds = new HashSet<>();
            UploadContext context = new UploadContext(
                    hospitalId,
                    dicomServer,
                    uploadedBy,
                    receivedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    uploadResponse,
                    summariesByStudyUid,
                    worklistsByStudyUid,
                    receivedWorklistIds
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
            processUploadedInstanceInTransaction(uploaded, context);
            context.response.setAcceptedFiles(context.response.getAcceptedFiles() + 1);
        } catch (Exception error) {
            context.response.setFailedFiles(context.response.getFailedFiles() + 1);
            addUploadError(context.response, filename, error);
        }
    }

    private void uploadZip(MultipartFile zipFile, UploadContext context) throws IOException {
        int entries = 0;
        long maxEntryBytes = configuredMaxZipEntryBytes();
        try (ZipInputStream zipInputStream = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path tempEntryPath = null;
                try {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    entries++;
                    if (entries > MAX_ZIP_ENTRIES) {
                        throw new IllegalArgumentException("ZIP contains too many files.");
                    }
                    tempEntryPath = writeBoundedZipEntryToTempFile(zipInputStream, maxEntryBytes);
                    long entryBytes = Files.size(tempEntryPath);
                    if (entryBytes == 0) {
                        continue;
                    }
                    if (entryBytes > maxEntryBytes) {
                        throw new IllegalArgumentException("ZIP entry is too large.");
                    }
                    DicomServerInstanceUploadResponse uploaded = dicomServerClientService.uploadInstance(
                            context.dicomServer.getBaseUrl(),
                            context.dicomServer.getUsername(),
                            context.dicomServer.getPassword(),
                            new FileSystemResource(tempEntryPath),
                            entryBytes
                    );
                    processUploadedInstanceInTransaction(uploaded, context);
                    context.response.setAcceptedFiles(context.response.getAcceptedFiles() + 1);
                } catch (Exception entryError) {
                    context.response.setFailedFiles(context.response.getFailedFiles() + 1);
                    addUploadError(context.response, entry.getName(), entryError);
                } finally {
                    deleteTempFile(tempEntryPath);
                    zipInputStream.closeEntry();
                }
            }
        }
    }

    private Path writeBoundedZipEntryToTempFile(ZipInputStream zipInputStream, long maxEntryBytes) throws IOException {
        Path tempDirectory = resolveDicomUploadTempDirectory();
        Path tempFile = Files.createTempFile(tempDirectory, "dicom-zip-entry-", ".dcm");
        byte[] buffer = new byte[ZIP_READ_BUFFER_BYTES];
        long total = 0;
        int read;
        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(tempFile), ZIP_READ_BUFFER_BYTES)) {
            while ((read = zipInputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxEntryBytes) {
                    throw new IllegalArgumentException("ZIP entry is too large.");
                }
                outputStream.write(buffer, 0, read);
            }
        } catch (Exception error) {
            deleteTempFile(tempFile);
            throw error;
        }
        return tempFile;
    }

    private Path resolveDicomUploadTempDirectory() throws IOException {
        String configured = firstNonBlank(dicomUploadTempDir, multipartLocation);
        Path tempDirectory = configured == null
                ? Paths.get(System.getProperty("java.io.tmpdir"), "udaya-pacs-dicom-upload")
                : Paths.get(configured).toAbsolutePath().normalize();
        ensureUploadTempDirectoryIsOutsideAppTree(tempDirectory);
        Files.createDirectories(tempDirectory);
        return tempDirectory;
    }

    private static void ensureUploadTempDirectoryIsOutsideAppTree(Path tempDirectory) {
        Path applicationDirectory = Paths.get("").toAbsolutePath().normalize();
        if (tempDirectory.startsWith(applicationDirectory)) {
            throw new IllegalStateException("DICOM upload temporary directory must be outside the API project folder.");
        }
        for (Path current = tempDirectory; current != null; current = current.getParent()) {
            Path fileName = current.getFileName();
            if (fileName == null) {
                continue;
            }
            String normalized = fileName.toString().toLowerCase(Locale.ROOT);
            if ("pacs_frontend".equals(normalized) || "pacs-frontend".equals(normalized)) {
                throw new IllegalStateException("DICOM upload temporary directory must be outside the frontend project folder.");
            }
        }
    }

    private long configuredMaxUploadBytes() {
        return maxDicomUploadRequestBytes > 0 ? maxDicomUploadRequestBytes : DEFAULT_MAX_DICOM_UPLOAD_BYTES;
    }

    private long configuredMaxZipEntryBytes() {
        return maxZipEntryBytes > 0 ? maxZipEntryBytes : configuredMaxUploadBytes();
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            long gib = bytes / (1024L * 1024L * 1024L);
            return gib + " GB";
        }
        if (bytes >= 1024L * 1024L) {
            long mib = bytes / (1024L * 1024L);
            return mib + " MB";
        }
        return bytes + " bytes";
    }

    private static void deleteTempFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            LOGGER.warn("Unable to delete temporary DICOM upload file {}: {}", path, error.toString());
        }
    }

    private void processUploadedInstanceInTransaction(DicomServerInstanceUploadResponse upload, UploadContext context) {
        if (transactionManager == null) {
            processUploadedInstance(upload, context);
            return;
        }
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.executeWithoutResult(status -> processUploadedInstance(upload, context));
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

        List<DicomServerSeriesResponse> series = dicomServerClientService.getSeriesByStudyId(
                context.dicomServer.getBaseUrl(),
                context.dicomServer.getUsername(),
                context.dicomServer.getPassword(),
                studyId
        );
        String firstSeriesId = firstSeriesId(study, series, upload);
        int instanceCount = Math.max(1, countInstances(study, series));
        String dicomModality = normalizeDicomModality(firstNonBlank(readDicomTag(studyTags, "Modality"), firstSeriesModality(series)));
        if (dicomModality == null) {
            throw new IllegalArgumentException("Uploaded DICOM has no Modality tag.");
        }
        ModalityResponse modality = resolveUploadModality(context.hospitalId, dicomModality);
        String modalityCode = firstNonBlank(modality.getAbbr(), modality.getName());
        if (modalityCode == null) {
            throw new IllegalArgumentException("Matched hospital modality has no configured code.");
        }

        String dicomPatientId = firstNonBlank(readDicomTag(patientTags, "PatientID"), study.getParentPatient());
        DicomPatientName patientName = splitPatientName(readDicomTag(patientTags, "PatientName"));
        LocalDate birthDate = parseDicomDate(readDicomTag(patientTags, "PatientBirthDate"));
        String gender = normalizePatientSex(readDicomTag(patientTags, "PatientSex"));
        PatientMatch patientMatch = findOrCreatePatient(context.hospitalId, dicomPatientId, patientName, birthDate, gender);
        PatientResponse patient = patientMatch.patient();

        String dicomAccessionNumber = firstNonBlank(readDicomTag(studyTags, "AccessionNumber"));
        String referenceVisitCode = dicomAccessionNumber;
        LocalDate studyDate = parseDicomDate(readDicomTag(studyTags, "StudyDate"));
        String studyDescription = firstNonBlank(readDicomTag(studyTags, "StudyDescription"), "Uploaded DICOM");
        UploadWorklistContext uploadWorklist = resolveOrCreateUploadWorklist(
                context,
                studyInstanceUid,
                study.getId(),
                patient,
                modality,
                studyDate,
                studyDescription,
                referenceVisitCode
        );
        String accessionNumber = uploadWorklist.visitCode();

        Long studyDbId = studyMapper.upsertFromDicomUpload(
                context.hospitalId,
                patient.getId(),
                studyInstanceUid,
                accessionNumber,
                referenceVisitCode,
                modality.getId(),
                modalityCode,
                studyDate,
                studyDescription,
                context.dicomServer.getId(),
                STATUS_IMAGE_RECEIVED,
                study.getId(),
                firstNonBlank(study.getParentPatient(), upload == null ? null : upload.getParentPatient()),
                firstSeriesId,
                instanceCount,
                context.uploadedBy,
                context.receivedAtIso
        );

        if (studyDbId == null || studyDbId <= 0L) {
            throw new IllegalStateException("Uploaded DICOM study was not saved.");
        }
        WorklistDetailRow linkedWorklist = markUploadWorklistReceived(context, uploadWorklist, patient.getId(), studyDbId, referenceVisitCode);
        if (linkedWorklist == null || linkedWorklist.getStudyId() == null || !studyDbId.equals(linkedWorklist.getStudyId())) {
            throw new IllegalStateException("Uploaded DICOM Worklist was not linked to the saved Study.");
        }
        StudyResponse savedStudy = studyMapper.findById(context.hospitalId, studyDbId);
        if (savedStudy == null) {
            throw new IllegalStateException("Uploaded DICOM Study was not available in Study Archive after save.");
        }

        DicomUploadStudySummary summary = context.summariesByStudyUid.computeIfAbsent(studyInstanceUid, ignored -> new DicomUploadStudySummary());
        summary.setStudyPublicKey(savedStudy.getPublicKey());
        summary.setPatientPublicKey(patient.getPublicKey());
        summary.setPatientCode(patient.getPatientCode());
        summary.setFirstName(patient.getFirstName());
        summary.setLastName(patient.getLastName());
        summary.setPatientBirthDate(patient.getDateOfBirth() == null ? null : patient.getDateOfBirth().toString());
        summary.setPatientSex(patient.getGender());
        summary.setPatientCreated(Boolean.TRUE.equals(patientMatch.created()));
        summary.setAccessionNumber(accessionNumber);
        summary.setReferenceVisitCode(referenceVisitCode);
        summary.setWorklistPublicKey(linkedWorklist == null ? uploadWorklist.publicKey() : linkedWorklist.getPublicKey());
        summary.setWorklistVisitCode(linkedWorklist == null ? uploadWorklist.visitCode() : linkedWorklist.getVisitCode());
        summary.setWorklistCreated(uploadWorklist.created());
        summary.setStudyInstanceUid(studyInstanceUid);
        summary.setStudyDescription(savedStudy.getStudyDescription());
        summary.setModality(modalityCode);
        summary.setModalityPublicKey(modality.getPublicKey());
        summary.setModalityName(modality.getName());
        summary.setDicomServerStudyId(study.getId());
        summary.setDicomServerPatientId(study.getParentPatient());
        summary.setDicomServerSeriesId(firstSeriesId);
        summary.setInstances(instanceCount);
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

    private UploadWorklistContext resolveOrCreateUploadWorklist(
            UploadContext context,
            String studyInstanceUid,
            String dicomServerStudyId,
            PatientResponse patient,
            ModalityResponse modality,
            LocalDate studyDate,
            String studyDescription,
            String referenceVisitCode
    ) {
        UploadWorklistContext cached = context.worklistsByStudyUid.get(studyInstanceUid);
        if (cached != null) {
            return cached;
        }

        WorklistDetailRow existing = worklistMapper.findWorklistByStudyIdentifiersAndHospital(
                context.hospitalId,
                studyInstanceUid,
                dicomServerStudyId
        );
        if (existing != null && existing.getId() != null && firstNonBlank(existing.getVisitCode()) != null) {
            UploadWorklistContext resolved = new UploadWorklistContext(
                    existing.getId(),
                    existing.getPublicKey(),
                    existing.getVisitCode(),
                    false
            );
            context.worklistsByStudyUid.put(studyInstanceUid, resolved);
            return resolved;
        }

        WorklistAssignRequest request = new WorklistAssignRequest();
        request.setPatientId(patient.getId());
        request.setModalityId(modality.getId());
        request.setDicomServerId(context.dicomServer.getId());
        request.setStudyDescription(studyDescription);
        request.setScheduledDate(studyDate == null ? LocalDate.now() : studyDate);
        request.setScheduledTime(LocalTime.now().withSecond(0).withNano(0));
        request.setNotes(buildUploadWorklistNote(referenceVisitCode));

        String visitCode = null;
        Boolean inserted = false;
        for (int attempt = 0; attempt < 10; attempt++) {
            visitCode = generateVisitCode(context.hospitalId, modality);
            if (worklistMapper.findWorklistByVisitCodeAnyHospital(visitCode) != null) {
                continue;
            }
            try {
                inserted = worklistMapper.assignWorklist(context.hospitalId, context.uploadedBy, visitCode, request);
                if (Boolean.TRUE.equals(inserted)) {
                    break;
                }
            } catch (DuplicateKeyException duplicate) {
                LOGGER.warn("visit_code collision detected for uploaded DICOM hospitalId={} visitCode={}, retrying", context.hospitalId, visitCode);
            }
        }
        if (!Boolean.TRUE.equals(inserted)) {
            throw new IllegalStateException("Unable to create Worklist for uploaded DICOM.");
        }

        WorklistDetailRow created = worklistMapper.findWorklistByVisitCode(context.hospitalId, visitCode);
        if (created == null || created.getId() == null) {
            throw new IllegalStateException("Uploaded DICOM Worklist was not found after creation.");
        }
        worklistMapper.insertHistory(
                context.hospitalId,
                created.getId(),
                created.getPatientId(),
                WorklistStatus.WAITING.code(),
                WorklistStatus.WAITING.code(),
                WorklistConstants.ACTION_ASSIGN,
                request.getNotes(),
                context.uploadedBy
        );

        UploadWorklistContext resolved = new UploadWorklistContext(
                created.getId(),
                created.getPublicKey(),
                created.getVisitCode(),
                true
        );
        context.worklistsByStudyUid.put(studyInstanceUid, resolved);
        return resolved;
    }

    private WorklistDetailRow markUploadWorklistReceived(
            UploadContext context,
            UploadWorklistContext uploadWorklist,
            Long patientId,
            Long studyDbId,
            String referenceVisitCode
    ) {
        if (uploadWorklist == null || uploadWorklist.id() == null || firstNonBlank(uploadWorklist.visitCode()) == null) {
            return null;
        }
        int updatedRows = worklistMapper.updateWorklistReceivedByVisitCode(
                context.hospitalId,
                uploadWorklist.visitCode(),
                studyDbId,
                context.receivedAtIso,
                WorklistStatus.IN_PROGRESS.code(),
                context.uploadedBy
        );
        if (updatedRows <= 0) {
            updatedRows = worklistMapper.updateWorklistReceivedById(
                    context.hospitalId,
                    uploadWorklist.id(),
                    studyDbId,
                    WorklistStatus.IN_PROGRESS.code(),
                    context.uploadedBy,
                    context.receivedAtIso
            );
        }
        if (updatedRows <= 0) {
            throw new IllegalStateException("Uploaded DICOM Worklist could not be marked as image received.");
        }
        worklistMapper.upsertWorklistStudyLink(context.hospitalId, uploadWorklist.id(), studyDbId, context.uploadedBy);
        boolean firstReceiveForUpload = context.receivedWorklistIds.add(uploadWorklist.id());
        if (uploadWorklist.created() && firstReceiveForUpload) {
            worklistMapper.insertHistory(
                    context.hospitalId,
                    uploadWorklist.id(),
                    patientId,
                    WorklistStatus.WAITING.code(),
                    WorklistStatus.IN_PROGRESS.code(),
                    WorklistConstants.ACTION_RECEIVED_STUDY,
                    buildUploadWorklistNote(referenceVisitCode),
                    context.uploadedBy
            );
        }
        return worklistMapper.findWorklistById(context.hospitalId, uploadWorklist.id());
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

    private String generateVisitCode(Long hospitalId, ModalityResponse modality) {
        String dateToken = FunctionCodeGenerate.currentVisitDateToken();
        String visitPrefix = resolveVisitPrefix(modality);
        String hospitalToken = resolveVisitHospitalToken(hospitalId);
        String sequenceKey = FunctionCodeGenerate.buildVisitSequenceKey(visitPrefix, hospitalToken, dateToken);
        Long nextSequence = worklistMapper.nextVisitSequence(hospitalId, sequenceKey);
        long sequence = nextSequence == null ? 1L : nextSequence;
        return FunctionCodeGenerate.buildVisitCode(visitPrefix, hospitalToken, dateToken, sequence);
    }

    private String resolveVisitHospitalToken(Long hospitalId) {
        if (hospitalId == null || hospitalId <= 0) {
            return "HOSP";
        }
        try {
            List<HospitalResponseDetail> hospitals = hospitalMapper.getHospitalById(hospitalId);
            if (hospitals != null && !hospitals.isEmpty() && hospitals.get(0) != null) {
                String token = FunctionHelper.normalizeHospitalToken(hospitals.get(0).getAbbr());
                if (FunctionHelper.isValidHospitalToken(token)) {
                    return token;
                }
            }
        } catch (Exception ignored) {
            return "H" + hospitalId;
        }
        return "H" + hospitalId;
    }

    private String resolveVisitPrefix(ModalityResponse modality) {
        if (modality == null) {
            return "OT";
        }
        String token = FunctionHelper.normalizeModalityToken(modality.getAbbr());
        if (FunctionHelper.hasText(token)) {
            return token;
        }
        token = FunctionHelper.normalizeModalityToken(modality.getName());
        return FunctionHelper.hasText(token) ? token : "OT";
    }

    private static String buildUploadWorklistNote(String referenceVisitCode) {
        String reference = firstNonBlank(referenceVisitCode);
        if (reference == null) {
            return "Created automatically from uploaded DICOM.";
        }
        return "Created automatically from uploaded DICOM. Original AccessionNumber: " + reference;
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
        if (raw.contains("^")) {
            String[] dicomParts = raw.split("\\^", -1);
            String familyName = trimToNull(dicomParts.length > 0 ? dicomParts[0] : null);
            String givenName = trimToNull(dicomParts.length > 1 ? dicomParts[1] : null);
            String middleName = trimToNull(dicomParts.length > 2 ? dicomParts[2] : null);
            String firstName = firstNonBlank(givenName, middleName, familyName);
            boolean usedFamilyNameAsFirst = familyName != null && familyName.equals(firstName);
            String lastName = usedFamilyNameAsFirst ? "" : firstNonBlank(familyName, "");
            return new DicomPatientName(firstName, lastName);
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

    private ModalityResponse resolveUploadModality(Long hospitalId, String dicomModality) {
        String normalized = normalizeDicomModality(dicomModality);
        if (normalized == null) {
            throw new IllegalArgumentException("Uploaded DICOM has no Modality tag.");
        }
        ModalityResponse modality = modalityMapper.findActiveHospitalModalityByDicomCode(hospitalId, normalized);
        if (modality == null || modality.getId() == null) {
            throw new IllegalArgumentException("DICOM Modality '" + normalized + "' is not active for the selected hospital.");
        }
        return modality;
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

    private static String normalizeDicomModality(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static int countInstances(DicomServerStudyResponse study, List<DicomServerSeriesResponse> series) {
        if (study != null && study.getInstances() != null && !study.getInstances().isEmpty()) {
            return study.getInstances().size();
        }
        Integer statisticsCount = study == null ? null : readDicomServerInstanceCount(study.getStatistics());
        if (statisticsCount != null && statisticsCount > 0) {
            return statisticsCount;
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
            Map<String, DicomUploadStudySummary> summariesByStudyUid,
            Map<String, UploadWorklistContext> worklistsByStudyUid,
            Set<Long> receivedWorklistIds
    ) {
    }

    private record UploadWorklistContext(Long id, String publicKey, String visitCode, boolean created) {
    }

    private record DicomServerResolution(HospitalDicomServerResponse server, String errorMessage) {
    }

    private record PatientMatch(PatientResponse patient, Boolean created) {
    }

    private record DicomPatientName(String firstName, String lastName) {
    }

}
