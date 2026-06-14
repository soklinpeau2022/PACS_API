package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.config.WorklistConstants;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.helper.dicomServer.DicomServerWorklistMapperHelper;
import com.ut.emrPacs.mapper.pacs.WorklistMapper;
import com.ut.emrPacs.mapper.pacs.StudyMapper;
import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerFindRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalModalityServerRouteResponse;
import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerWorklistCreateRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerStudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.PacsResultSyncResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import com.ut.emrPacs.model.enums.WorklistStatus;
import com.ut.emrPacs.model.enums.StudyStatus;
import com.ut.emrPacs.service.service.DicomServerClientService;
import com.ut.emrPacs.service.service.PacsResultSyncService;
import com.ut.emrPacs.service.service.ActivityLogService;
import static com.ut.emrPacs.helper.FunctionHelper.firstNonBlank;
import static com.ut.emrPacs.helper.FunctionHelper.firstNonNull;
import static com.ut.emrPacs.helper.FunctionHelper.hasText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PacsResultSyncServiceImpl implements PacsResultSyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PacsResultSyncServiceImpl.class);
    private static final int DEFAULT_AUTO_SEND_BATCH_SIZE = 100;
    private static final String DEFAULT_DICOM_AE_TITLE = "UDAYA";

    private final WorklistMapper WorklistMapper;
    private final StudyMapper studyMapper;
    private final DicomServerMapper dicomServerMapper;
    private final DicomServerClientService dicomServerClientService;
    private final ActivityLogService activityLogService;

    public PacsResultSyncServiceImpl(
            WorklistMapper WorklistMapper,
            StudyMapper studyMapper,
            DicomServerMapper dicomServerMapper,
            DicomServerClientService dicomServerClientService,
            ActivityLogService activityLogService
    ) {
        this.WorklistMapper = WorklistMapper;
        this.studyMapper = studyMapper;
        this.dicomServerMapper = dicomServerMapper;
        this.dicomServerClientService = dicomServerClientService;
        this.activityLogService = activityLogService;
    }

    @Override
    public int autoSendWaitingWorklists() {
        List<WorklistDetailRow> waitingWorklists = WorklistMapper.listWaitingWorklistForAutoSend(DEFAULT_AUTO_SEND_BATCH_SIZE);
        if (waitingWorklists == null || waitingWorklists.isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (WorklistDetailRow Worklist : waitingWorklists) {
            LocalTime startDuration = LocalTime.now();
            try {
                if (sendWaitingWorklistToDicomServer(Worklist)) {
                    processed++;
                }
            } catch (HttpClientErrorException.Unauthorized unauthorized) {
                LOGGER.warn("Auto-send Worklist {} unauthorized (401).", Worklist != null ? Worklist.getId() : null);
                insertSyncErrorLog("/internal/PacsResultSyncServiceImpl/autoSendWaitingWorklists", unauthorized, startDuration, "PACS Result Sync", "PACS Auto Send (401)", "AutoSend");
            } catch (HttpClientErrorException.NotFound notFound) {
                LOGGER.warn("Auto-send Worklist {} failed: worklists/create not found (404).", Worklist != null ? Worklist.getId() : null);
                insertSyncErrorLog("/internal/PacsResultSyncServiceImpl/autoSendWaitingWorklists", notFound, startDuration, "PACS Result Sync", "PACS Auto Send (404)", "AutoSend");
            } catch (ResourceAccessException unreachable) {
                LOGGER.warn("Auto-send Worklist {} failed: DicomServer unreachable.", Worklist != null ? Worklist.getId() : null);
                insertSyncErrorLog("/internal/PacsResultSyncServiceImpl/autoSendWaitingWorklists", unreachable, startDuration, "PACS Result Sync", "PACS Auto Send (Unreachable)", "AutoSend");
            } catch (Exception error) {
                LOGGER.error("Auto-send Worklist {} failed: {}", Worklist != null ? Worklist.getId() : null, error.toString(), error);
                insertSyncErrorLog("/internal/PacsResultSyncServiceImpl/autoSendWaitingWorklists", error, startDuration, "PACS Result Sync", "PACS Auto Send", "AutoSend");
            }
        }
        return processed;
    }

    @Override
    public PacsResultSyncResponse syncPacsResultByAccessionNumber(String accessionNumber) {
        return syncPacsResultByAccessionNumber(null, accessionNumber);
    }

    @Override
    public PacsResultSyncResponse syncPacsResultByAccessionNumber(Long hospitalId, String accessionNumber) {
        PacsResultSyncResponse response = new PacsResultSyncResponse();
        response.setAccessionNumber(accessionNumber);

        if (!hasText(accessionNumber)) {
            response.setMessage("Missing accession number.");
            return response;
        }

        WorklistDetailRow Worklist = hospitalId != null && hospitalId > 0
                ? WorklistMapper.findWorklistByAccessionNumberAndHospital(hospitalId, accessionNumber.trim())
                : WorklistMapper.findWorklistByAccessionNumber(accessionNumber.trim());
        if (Worklist == null) {
            response.setMessage("Worklist not found by accession number.");
            return response;
        }
        return syncByWorklist(Worklist);
    }

    @Override
    public List<PacsResultSyncResponse> syncPendingPacsResults() {
        List<WorklistDetailRow> pendingWorklists = WorklistMapper.listPendingPacsSyncWorklist();
        if (pendingWorklists == null || pendingWorklists.isEmpty()) {
            return List.of();
        }
        return pendingWorklists.stream()
                .map(this::syncByWorklist)
                .toList();
    }

    private PacsResultSyncResponse syncByWorklist(WorklistDetailRow Worklist) {
        PacsResultSyncResponse response = new PacsResultSyncResponse();
        if (Worklist == null) {
            response.setMessage("Worklist payload is missing.");
            return response;
        }

        response.setWorklistId(Worklist.getId());
        response.setWorklistPublicKey(Worklist.getPublicKey());
        response.setAccessionNumber(Worklist.getAccessionNumber());
        response.setStatus(Worklist.getStatus());
        response.setDicomServerStudyId(Worklist.getDicomServerStudyId());
        response.setStudyInstanceUid(Worklist.getStudyInstanceUid());
        response.setViewerUrl(Worklist.getViewerUrl());

        if (!hasText(Worklist.getAccessionNumber())) {
            response.setMessage("Missing accession number.");
            return response;
        }
        if (WorklistStatus.CANCELLED.name().equalsIgnoreCase(Worklist.getStatus())) {
            response.setMessage("Worklist is already CANCELLED.");
            return response;
        }

        try {
            HospitalDicomServerResponse server = resolveViewerDicomServer(Worklist);
            if (server == null) {
                response.setMessage("Active DICOM server routing is not configured for this Worklist.");
                return response;
            }

            List<String> studyIds = findStudyByAccessionNumber(Worklist.getAccessionNumber(), server);
            if (studyIds == null || studyIds.isEmpty()) {
                response.setMessage("Result not received yet.");
                return response;
            }

            String studyId = studyIds.get(0);
            DicomServerStudyResponse studyResponse = dicomServerClientService.getStudyById(
                    resolveDicomServerBaseUrl(server),
                    server.getUsername(),
                    server.getPassword(),
                    studyId
            );
            if (!hasStudyInstances(studyResponse)) {
                response.setMessage("Result found but DicomServer study has no image instances yet.");
                return response;
            }
            updateWorklistToReceived(Worklist, studyResponse);

            response.setStatus(WorklistStatus.IN_PROGRESS.name());
            response.setDicomServerStudyId(studyId);
            response.setStudyInstanceUid(readDicomTag(studyResponse, "StudyInstanceUID"));
            response.setViewerUrl(buildViewerUrl(Worklist, studyId));
            response.setMessage(studyIds.size() > 1
                    ? "Multiple studies found for accession number. First study applied."
                    : "Result received from DicomServer.");
            return response;
        } catch (HttpClientErrorException.Unauthorized unauthorized) {
            insertSyncErrorLog("/internal/PacsResultSyncServiceImpl/syncByWorklist", unauthorized, LocalTime.now(), "PACS Result Sync", "PACS Sync (401)", "Sync");
            response.setMessage("DicomServer unauthorized (401). Check username/password.");
            return response;
        } catch (HttpClientErrorException.NotFound notFound) {
            insertSyncErrorLog("/internal/PacsResultSyncServiceImpl/syncByWorklist", notFound, LocalTime.now(), "PACS Result Sync", "PACS Sync (404)", "Sync");
            response.setMessage("DicomServer endpoint not found (404). Worklists plugin may be disabled.");
            return response;
        } catch (ResourceAccessException unreachable) {
            insertSyncErrorLog("/internal/PacsResultSyncServiceImpl/syncByWorklist", unreachable, LocalTime.now(), "PACS Result Sync", "PACS Sync (Unreachable)", "Sync");
            response.setMessage("DicomServer unreachable.");
            return response;
        } catch (HttpServerErrorException serverError) {
            insertSyncErrorLog("/internal/PacsResultSyncServiceImpl/syncByWorklist", serverError, LocalTime.now(), "PACS Result Sync", "PACS Sync (5xx)", "Sync");
            response.setMessage("DicomServer server error.");
            return response;
        } catch (Exception error) {
            LOGGER.error("syncByWorklist failed: {}", error.toString(), error);
            insertSyncErrorLog("/internal/PacsResultSyncServiceImpl/syncByWorklist", error, LocalTime.now(), "PACS Result Sync", "PACS Sync", "Sync");
            response.setMessage("Failed to sync PACS result.");
            return response;
        }
    }

    private void insertSyncErrorLog(
            String endpoint,
            Exception error,
            LocalTime startDuration,
            String moduleName,
            String moduleId,
            String action
    ) {
        try {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = resolveErrorLine(error);
            activityLogService.insert(
                    endpoint,
                    errorLine,
                    error != null ? error.toString() : "Unknown error",
                    moduleName,
                    moduleId,
                    action,
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    null
            );
        } catch (Exception ex) {
            LOGGER.debug("Activity log insert failed in sync scheduler: {}", ex.getMessage());
        }
    }

    private static Long resolveErrorLine(Exception error) {
        if (error == null || error.getStackTrace() == null || error.getStackTrace().length == 0) {
            return null;
        }
        return (long) error.getStackTrace()[0].getLineNumber();
    }

    private List<String> findStudyByAccessionNumber(String accessionNumber, HospitalDicomServerResponse server) {
        DicomServerFindRequest findRequest = new DicomServerFindRequest();
        findRequest.setLevel("Study");
        Map<String, String> query = new HashMap<>();
        query.put("AccessionNumber", accessionNumber);
        findRequest.setQuery(query);
        return dicomServerClientService.findStudyIdsByAccessionNumber(
                resolveDicomServerBaseUrl(server),
                server.getUsername(),
                server.getPassword(),
                findRequest
        );
    }

    private boolean sendWaitingWorklistToDicomServer(WorklistDetailRow Worklist) {
        if (Worklist == null || !WorklistStatus.WAITING.name().equalsIgnoreCase(Worklist.getStatus())) {
            return false;
        }

        String accessionNumber = buildAccessionNumber(Worklist);
        if (!hasText(accessionNumber)) {
            return false;
        }

        HospitalDicomServerResponse server = resolveViewerDicomServer(Worklist);
        if (server == null) {
            LOGGER.warn("Auto-send Worklist {} skipped because no active DICOM server is configured.", Worklist.getId());
            return false;
        }
        HospitalModalityServerRouteResponse route = resolveRouteForAutoSend(Worklist, server);
        String machineAeTitle = normalizeScheduledStationAeTitle(firstNonBlank(
                Worklist.getMachineAeTitle(),
                route == null ? null : route.getMachineAeTitle(),
                server.getAeTitle(),
                DEFAULT_DICOM_AE_TITLE
        ));

        String modalityCode = DicomServerWorklistMapperHelper.normalizeModality(firstNonBlank(Worklist.getModalityCode(), Worklist.getModalityName()));
        String studyDescription = firstNonBlank(
                Worklist.getStudyDescription(),
                DicomServerWorklistMapperHelper.defaultStudyDescription(null, Worklist.getModalityName())
        );
        LocalDate scheduledDate = Worklist.getScheduledDate() != null ? Worklist.getScheduledDate() : LocalDate.now();
        LocalTime scheduledTime = Worklist.getScheduledTime() != null
                ? Worklist.getScheduledTime()
                : LocalTime.now().plusMinutes(5).withSecond(0).withNano(0);
        DicomServerWorklistCreateRequest payload = DicomServerWorklistMapperHelper.toCreateRequest(
                Worklist,
                accessionNumber,
                modalityCode,
                studyDescription,
                scheduledDate,
                scheduledTime,
                machineAeTitle
        );
        var worklistResponse = dicomServerClientService.postToDicomServerWorklist(
                resolveDicomServerBaseUrl(server) + "/worklists/create",
                server.getUsername(),
                server.getPassword(),
                payload
        );

        int updated = WorklistMapper.updateWorklistSentToPacsById(
                Worklist.getHospitalId(),
                Worklist.getId(),
                WorklistStatus.IN_PROGRESS.code(),
                server.getId(),
                route == null ? null : route.getId(),
                accessionNumber,
                modalityCode,
                machineAeTitle,
                studyDescription,
                scheduledDate,
                scheduledTime,
                worklistResponse != null ? worklistResponse.getId() : null,
                worklistResponse != null ? worklistResponse.getPath() : null,
                null
        );
        if (updated > 0) {
            WorklistMapper.insertHistory(
                    Worklist.getHospitalId(),
                    Worklist.getId(),
                    Worklist.getPatientId(),
                    WorklistStatus.WAITING.code(),
                    WorklistStatus.IN_PROGRESS.code(),
                    WorklistConstants.ACTION_SEND_WORKLIST,
                    "auto-send accessionNumber=" + accessionNumber,
                    null
            );
            return true;
        }
        return false;
    }

    private void updateWorklistToReceived(WorklistDetailRow Worklist, DicomServerStudyResponse studyResponse) {
        if (Worklist == null || studyResponse == null) {
            return;
        }
        if (!hasStudyInstances(studyResponse)) {
            return;
        }
        String studyId = studyResponse.getId();
        String studyInstanceUid = readDicomTag(studyResponse, "StudyInstanceUID");
        String accessionNumber = readDicomTag(studyResponse, "AccessionNumber");
        String viewerUrl = buildViewerUrl(Worklist, studyId);
        String receivedAtIso = OffsetDateTime.now().toString();
        Long linkedStudyId = upsertStudyArchive(Worklist, studyResponse, studyId, studyInstanceUid, viewerUrl, receivedAtIso);

        int updated = WorklistMapper.updateWorklistReceivedById(
                Worklist.getHospitalId(),
                Worklist.getId(),
                linkedStudyId,
                WorklistStatus.IN_PROGRESS.code(),
                null,
                receivedAtIso
        );
        if (updated > 0) {
            if (linkedStudyId != null && linkedStudyId > 0L) {
                WorklistMapper.upsertWorklistStudyLink(
                        Worklist.getHospitalId(),
                        Worklist.getId(),
                        linkedStudyId,
                        null
                );
            }
            WorklistMapper.insertHistory(
                    Worklist.getHospitalId(),
                    Worklist.getId(),
                    Worklist.getPatientId(),
                    WorklistStatus.fromValue(Worklist.getStatus()).code(),
                    WorklistStatus.IN_PROGRESS.code(),
                    WorklistConstants.ACTION_SYNC_PACS_RESULT,
                    "Synced by accessionNumber=" + accessionNumber,
                    null
            );
        }
    }

    private static boolean hasStudyInstances(DicomServerStudyResponse studyResponse) {
        if (studyResponse == null) {
            return false;
        }
        if (studyResponse.getInstances() != null && !studyResponse.getInstances().isEmpty()) {
            return true;
        }
        Integer count = readDicomServerInstanceCount(studyResponse.getStatistics());
        return count != null && count > 0;
    }

    private static Integer readDicomServerInstanceCount(Map<String, Object> statistics) {
        if (statistics == null || statistics.isEmpty()) {
            return null;
        }
        Object value = firstNonNull(
                statistics.get("CountInstances"),
                statistics.get("Instances"),
                statistics.get("TotalInstances"),
                statistics.get("InstanceCount")
        );
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long upsertStudyArchive(
            WorklistDetailRow Worklist,
            DicomServerStudyResponse studyResponse,
            String dicomServerStudyId,
            String studyInstanceUid,
            String viewerUrl,
            String receivedAtIso
    ) {
        if (Worklist == null || !hasText(studyInstanceUid)) {
            return null;
        }
        return studyMapper.upsertFromWorklist(
                Worklist.getHospitalId(),
                Worklist.getPatientId(),
                studyInstanceUid,
                firstNonBlank(readDicomTag(studyResponse, "AccessionNumber"), Worklist.getAccessionNumber()),
                Worklist.getModalityId(),
                firstNonBlank(Worklist.getModalityCode(), Worklist.getModalityName(), readDicomTag(studyResponse, "ModalitiesInStudy"), readDicomTag(studyResponse, "Modality")),
                parseDicomStudyDate(readDicomTag(studyResponse, "StudyDate")),
                firstNonBlank(readDicomTag(studyResponse, "StudyDescription"), Worklist.getStudyDescription()),
                Worklist.getDicomServerId(),
                StudyStatus.IMAGE_RECEIVED.code(),
                dicomServerStudyId,
                studyResponse != null ? studyResponse.getParentPatient() : null,
                studyResponse != null && studyResponse.getSeries() != null && !studyResponse.getSeries().isEmpty() ? studyResponse.getSeries().get(0) : null,
                receivedAtIso
        );
    }

    private String buildViewerUrl(WorklistDetailRow Worklist, String studyId) {
        if (!hasText(studyId) || Worklist == null || Worklist.getHospitalId() == null) {
            return null;
        }
        HospitalDicomServerResponse server = resolveViewerDicomServer(Worklist);
        String publicUiBaseUrl = normalizeBaseUrl(server == null ? null : server.getDicomServerUiBaseUrl());
        if (!hasText(publicUiBaseUrl)) {
            publicUiBaseUrl = normalizeBaseUrl(resolveDicomServerBaseUrl(server));
        }
        if (!hasText(publicUiBaseUrl)) {
            return null;
        }
        return publicUiBaseUrl + "/app/explorer.html#study?uuid=" + studyId.trim();
    }

    private HospitalDicomServerResponse resolveViewerDicomServer(WorklistDetailRow Worklist) {
        if (Worklist == null || Worklist.getHospitalId() == null) {
            return null;
        }
        if (Worklist.getId() != null && Worklist.getId() > 0L) {
            HospitalDicomServerResponse server = dicomServerMapper.findActiveDicomServerByWorklist(Worklist.getHospitalId(), Worklist.getId());
            if (server != null) {
                return server;
            }
        }
        HospitalDicomServerResponse routedServer = resolveDicomServerByHospitalModalityRoute(Worklist);
        if (routedServer != null) {
            return routedServer;
        }
        String machineAeTitle = Worklist.getMachineAeTitle();
        if (hasText(machineAeTitle)) {
            return dicomServerMapper.findActiveDicomServerByHospitalAndAeTitle(Worklist.getHospitalId(), machineAeTitle.trim());
        }
        return dicomServerMapper.findActiveDicomServerByHospitalAndAeTitle(Worklist.getHospitalId(), DEFAULT_DICOM_AE_TITLE);
    }

    private HospitalDicomServerResponse resolveDicomServerByHospitalModalityRoute(WorklistDetailRow worklist) {
        if (worklist == null || worklist.getHospitalId() == null || worklist.getModalityId() == null) {
            return null;
        }
        List<HospitalModalityServerRouteResponse> routes = dicomServerMapper.listActiveRoutesByHospitalAndModality(
                worklist.getHospitalId(),
                worklist.getModalityId()
        );
        if (routes == null || routes.isEmpty()) {
            return null;
        }
        for (HospitalModalityServerRouteResponse route : routes) {
            if (route == null || route.getDicomServerId() == null || route.getDicomServerId() <= 0L) {
                continue;
            }
            List<HospitalDicomServerResponse> servers = dicomServerMapper.getDicomServerById(route.getDicomServerId(), worklist.getHospitalId());
            if (servers != null && !servers.isEmpty() && servers.get(0) != null) {
                return servers.get(0);
            }
        }
        return null;
    }

    private HospitalModalityServerRouteResponse resolveRouteForAutoSend(WorklistDetailRow worklist, HospitalDicomServerResponse server) {
        if (worklist == null || worklist.getHospitalId() == null || worklist.getModalityId() == null) {
            return null;
        }
        List<HospitalModalityServerRouteResponse> routes = dicomServerMapper.listActiveRoutesByHospitalAndModality(
                worklist.getHospitalId(),
                worklist.getModalityId()
        );
        if (routes == null || routes.isEmpty()) {
            return null;
        }
        Long serverId = server == null ? worklist.getDicomServerId() : server.getId();
        for (HospitalModalityServerRouteResponse route : routes) {
            if (route == null || !hasText(route.getMachineAeTitle())) {
                continue;
            }
            if (serverId == null || route.getDicomServerId() == null || serverId.equals(route.getDicomServerId())) {
                return route;
            }
        }
        return null;
    }

    private String normalizeScheduledStationAeTitle(String aeTitle) {
        String normalized = firstNonBlank(aeTitle, DEFAULT_DICOM_AE_TITLE).trim();
        if (("MODALITY" + "SIM").equalsIgnoreCase(normalized)) {
            return DEFAULT_DICOM_AE_TITLE;
        }
        return normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private String resolveDicomServerBaseUrl(HospitalDicomServerResponse server) {
        if (server == null) {
            return null;
        }
        String configuredBaseUrl = normalizeBaseUrl(server.getBaseUrl());
        if (hasText(configuredBaseUrl)) {
            return configuredBaseUrl;
        }
        String ipAddress = normalizeBaseUrl(server.getIpAddress());
        if (!hasText(ipAddress)) {
            return null;
        }
        String protocol = ipAddress.startsWith("http://") || ipAddress.startsWith("https://")
                ? ""
                : "http://";
        String host = ipAddress.replaceFirst("^https?://", "");
        Integer port = server.getPort();
        return protocol + host + (port != null && port > 0 ? ":" + port : "");
    }

    private String normalizeBaseUrl(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("/+$", "");
    }

    private static String readDicomTag(DicomServerStudyResponse response, String tag) {
        if (response == null || response.getMainDicomTags() == null || tag == null) {
            return null;
        }
        Object value = response.getMainDicomTags().get(tag);
        return value == null ? null : String.valueOf(value);
    }

    private static LocalDate parseDicomStudyDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        try {
            if (normalized.length() == 8 && normalized.chars().allMatch(Character::isDigit)) {
                return LocalDate.parse(normalized, DateTimeFormatter.BASIC_ISO_DATE);
            }
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String buildAccessionNumber(WorklistDetailRow Worklist) {
        if (Worklist == null) {
            return null;
        }
        if (hasText(Worklist.getAccessionNumber())) {
            return Worklist.getAccessionNumber().trim();
        }
        return firstNonBlank(Worklist.getVisitCode(), "VISIT");
    }
}
