package com.ut.emrPacs.service.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.authentication.util.ViewerAccessKeyService;
import com.ut.emrPacs.authentication.util.ViewerAccessKeyService.ViewerAccessClaims;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.hospital.HospitalMapper;
import com.ut.emrPacs.mapper.modality.ModalityMapper;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.mapper.pacs.PacsResultMapper;
import com.ut.emrPacs.mapper.pacs.WorklistMapper;
import com.ut.emrPacs.mapper.pacs.StudyMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultContextRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultFindByWorklistRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultFindByStudyRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultImageRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultImageUploadRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultSaveRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultTemplateListRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateRequest;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultContextResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultImageResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultTemplateResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsViewerStateResponse;
import com.ut.emrPacs.model.dto.response.pacs.study.StudyResponse;
import com.ut.emrPacs.model.dto.response.systemSettings.hospital.HospitalResponseDetail;
import com.ut.emrPacs.model.dto.response.systemSettings.modality.ModalityResponse;
import com.ut.emrPacs.model.enums.StudyStatus;
import com.ut.emrPacs.service.service.PacsResultService;
import jakarta.servlet.http.HttpServletRequest;
import com.ut.emrPacs.helper.FunctionHelper;
import static com.ut.emrPacs.helper.FunctionHelper.firstNonNull;
import static com.ut.emrPacs.helper.FunctionHelper.hasText;
import static com.ut.emrPacs.helper.FunctionHelper.trimToNull;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PacsResultServiceImpl implements PacsResultService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PacsResultServiceImpl.class);
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final String RESULT_STATUS_IMAGE_RECEIVED = "IMAGE_RECEIVED";
    private static final String RESULT_STATUS_COMPLETED = "COMPLETED";
    private static final String DEFAULT_VIEWER_STATE_TYPE = "OHIF_VIEWER_STATE";
    private static final int DEFAULT_VIEWER_STATE_SCHEMA_VERSION = 2;
    private static final int MAX_VIEWER_STATE_SCHEMA_VERSION = 1000;
    private static final int MAX_VIEWER_STATE_JSON_BYTES = 10 * 1024 * 1024;
    private static final int MAX_VIEWER_STATE_JSON_DEPTH = 64;
    private static final long MAX_VIEWER_STATE_JSON_NODES = 1_000_000L;
    private static final int MAX_VIEWER_STATE_ARRAY_ITEMS = 250_000;

    @Autowired
    private PacsResultMapper pacsResultMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private WorklistMapper WorklistMapper;
    @Autowired
    private StudyMapper studyMapper;
    @Autowired
    private DicomServerMapper dicomServerMapper;
    @Autowired
    private HospitalMapper hospitalMapper;
    @Autowired
    private ModalityMapper modalityMapper;
    @Autowired
    private MessageService messageService;
    @Autowired
    private ViewerAccessKeyService viewerAccessKeyService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @Value("${pacs.result.static-auth.enabled:true}")
    private boolean staticAuthEnabled;
    @Value("${pacs.result.static-auth.api-key:}")
    private String configuredApiKey;
    @Value("${pacs.result.upload-root:/var/ut-image}")
    private String uploadRoot;
    @Value("${pacs.result.max-image-bytes:10485760}")
    private long maxImageBytes;

    @Override
    public boolean hasStaticResultAuth(HttpServletRequest request) {
        if (!staticAuthEnabled) {
            return true;
        }
        if (!hasText(configuredApiKey)) {
            return false;
        }
        String provided = firstNonBlank(
                request.getHeader("X-PACS-RESULT-API-KEY"),
                request.getHeader("X-API-KEY")
        );
        return constantTimeEquals(configuredApiKey.trim(), provided == null ? "" : provided.trim());
    }

    @Override
    @Transactional
    public ResponseMessage<BaseResult> create(PacsResultSaveRequest request, List<MultipartFile> images, HttpServletRequest httpServletRequest) throws UnknownHostException {
        boolean transactionMutated = false;
        try {
            resolveSavePublicKeys(request);
            ResultAccess access = authorizeSaveRequest(request, httpServletRequest);
            applyAccessClaims(request, access.claims());
            PacsResultSaveRequest safeRequest = normalizeAndEnrich(request);
            validateRequestScope(access.claims(), safeRequest);
            validateSaveRequest(safeRequest, images, false);

            PacsResultResponse existing = pacsResultMapper.findExisting(safeRequest);
            validateWritableResult(existing, findViewerStateForResult(safeRequest), access.claims());

            Long resultId;
            String status = resultStatus(safeRequest.getCompleted());
            transactionMutated = true;
            if (existing != null) {
                safeRequest.setId(existing.getId());
                pacsResultMapper.updateResult(safeRequest, status, null);
                resultId = existing.getId();
            } else {
                resultId = pacsResultMapper.insertResult(safeRequest, status, access.claims().userId());
            }

            storeImages(resultId, safeRequest.getHospitalId(), safeRequest.getModalityId(), images);
            updateStudyStatusIfPossible(safeRequest);

            PacsResultResponse response = loadResult(resultId);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(response), true));
        } catch (IllegalArgumentException validation) {
            if (transactionMutated) {
                throw validation;
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        } catch (ResultAccessException accessError) {
            if (transactionMutated) {
                throw accessError;
            }
            return resultAccessDenied(accessError);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to save PACS result.", error);
        }
    }

    @Override
    public ResponseMessage<BaseResult> findByStudy(PacsResultFindByStudyRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        ResultAccess access;
        try {
            resolveFindByStudyPublicKeys(request);
            access = authorizeFindByStudyRequest(request, httpServletRequest, false);
        } catch (ResultAccessException accessError) {
            return resultAccessDenied(accessError);
        } catch (IllegalArgumentException validation) {
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        }
        if (request == null || request.getHospitalId() == null || request.getModalityId() == null) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("hospitalKey and modalityKey are required.", false));
        }
        String safeStudyId = firstNonBlank(request.getStudyId(), request.getStudyInstanceUid(), "");
        if (!hasText(safeStudyId)) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("studyKey or studyInstanceUid is required.", false));
        }
        safeStudyId = safeStudyId.trim();
        PacsResultResponse result = safeStudyId.matches("^[0-9]+$")
                ? pacsResultMapper.findByStudyId(request.getHospitalId(), request.getModalityId(), Long.valueOf(safeStudyId))
                : pacsResultMapper.findByStudyInstanceUid(request.getHospitalId(), request.getModalityId(), safeStudyId);
        if (result == null) {
            return ResponseMessageUtils.makeResponse(true, messageService.message("No result found.", List.of(), true));
        }
        if (!resultMatchesAccess(result, access.claims())) {
            return resultAccessDenied(ResultAccessException.forbidden("Viewer access is not allowed for this result."));
        }
        normalizeResultResponse(result);
        attachImages(result);
        return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(result), true));
    }

    @Override
    public ResponseMessage<BaseResult> findByWorklist(PacsResultFindByWorklistRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        ResultAccess access;
        try {
            resolveFindByWorklistPublicKeys(request);
            access = authorizeFindByWorklistRequest(request, httpServletRequest, false);
        } catch (ResultAccessException accessError) {
            return resultAccessDenied(accessError);
        } catch (IllegalArgumentException validation) {
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        }
        if (request == null || request.getWorklistId() == null || request.getWorklistId() <= 0 || request.getHospitalId() == null || request.getHospitalId() <= 0) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("worklistId and hospitalId are required.", false));
        }
        PacsResultResponse result = pacsResultMapper.findByWorklist(request);
        if (result == null) {
            return ResponseMessageUtils.makeResponse(true, messageService.message("No result found.", List.of(), true));
        }
        if (!resultMatchesAccess(result, access.claims())) {
            return resultAccessDenied(ResultAccessException.forbidden("Viewer access is not allowed for this result."));
        }
        normalizeResultResponse(result);
        attachImages(result);
        return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(result), true));
    }

    @Override
    public ResponseMessage<BaseResult> getContext(PacsResultContextRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        ResultAccess access;
        try {
            resolveContextPublicKeys(request);
            access = authorizeContextRequest(request, httpServletRequest, false);
        } catch (ResultAccessException accessError) {
            return resultAccessDenied(accessError);
        } catch (IllegalArgumentException validation) {
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        }
        if (request == null) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("Request is required.", false));
        }
        applyAccessClaims(request, access.claims());
        trimContextRequest(request);
        if (request.getWorklistId() == null
                && request.getStudyId() == null
                && !hasText(request.getStudyInstanceUid())
                && !hasText(request.getAccessionNumber())) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("worklistId, studyId, studyInstanceUid, or accessionNumber is required.", false));
        }

        PacsResultContextResponse context = resolveResultContext(request);
        if (context == null) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("PACS result context was not found for this viewer study.", false));
        }
        if (context.getHospitalId() == null || context.getHospitalId() <= 0) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("Hospital context was not found for this viewer study.", false));
        }
        if (context.getModalityId() == null || context.getModalityId() <= 0) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("Modality context was not found for this viewer study.", false));
        }
        if (!hasText(context.getStatus())) {
            context.setStatus(RESULT_STATUS_IMAGE_RECEIVED);
        }
        if (!contextMatchesAccess(context, access.claims())) {
            return resultAccessDenied(ResultAccessException.forbidden("Viewer access is not allowed for this study."));
        }
        return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(context), true));
    }

    @Override
    @Transactional
    public ResponseMessage<BaseResult> update(PacsResultSaveRequest request, List<MultipartFile> images, HttpServletRequest httpServletRequest) throws UnknownHostException {
        boolean transactionMutated = false;
        try {
            resolveSavePublicKeys(request);
            ResultAccess access = authorizeSaveRequest(request, httpServletRequest);
            PacsResultResponse existing = findExistingForUpdate(request);
            if (existing == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("PACS result not found.", false));
            }
            Long resultId = existing.getId();
            validateWritableResult(existing, findViewerStateForResult(existing), access.claims());
            attachImages(existing);

            applyAccessClaims(request, access.claims());
            PacsResultSaveRequest safeRequest = normalizeAndEnrich(request);
            safeRequest.setId(resultId);
            safeRequest.setResultKey(existing.getResultKey());
            validateRequestScope(access.claims(), safeRequest);
            validateSaveRequest(safeRequest, images, existing.getImages() != null && !existing.getImages().isEmpty());

            String status = resultStatus(safeRequest.getCompleted());
            transactionMutated = true;
            pacsResultMapper.updateResult(safeRequest, status, null);
            storeImages(resultId, safeRequest.getHospitalId(), safeRequest.getModalityId(), images);
            updateStudyStatusIfPossible(safeRequest);

            PacsResultResponse response = loadResult(resultId);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(response), true));
        } catch (IllegalArgumentException validation) {
            if (transactionMutated) {
                throw validation;
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        } catch (ResultAccessException accessError) {
            if (transactionMutated) {
                throw accessError;
            }
            return resultAccessDenied(accessError);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to update PACS result.", error);
        }
    }

    @Override
    @Transactional
    public ResponseMessage<BaseResult> uploadImages(PacsResultImageUploadRequest request, List<MultipartFile> images, HttpServletRequest httpServletRequest) throws UnknownHostException {
        boolean transactionMutated = false;
        try {
            PacsResultResponse result = findExistingForImageUpload(request);
            if (result == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("PACS result not found.", false));
            }
            Long resultId = result.getId();
            ResultAccess access = authorizeExistingResult(result, httpServletRequest, true);
            validateWritableResult(result, findViewerStateForResult(result), access.claims());
            if (images == null || images.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("At least one image file is required.", false));
            }
            transactionMutated = true;
            storeImages(resultId, result.getHospitalId(), result.getModalityId(), images);
            PacsResultResponse response = loadResult(resultId);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(response), true));
        } catch (IllegalArgumentException validation) {
            if (transactionMutated) {
                throw validation;
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        } catch (ResultAccessException accessError) {
            if (transactionMutated) {
                throw accessError;
            }
            return resultAccessDenied(accessError);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to upload PACS result images.", error);
        }
    }

    private PacsResultResponse findExistingForImageUpload(PacsResultImageUploadRequest request) {
        if (request == null) {
            return null;
        }
        String resultKey = trimToNull(request.getResultKey());
        if (hasText(resultKey)) {
            try {
                UUID.fromString(resultKey);
            } catch (Exception ex) {
                LOGGER.debug("Invalid UUID format for resultKey: {}", ex.getMessage());
                throw new IllegalArgumentException("Invalid resultKey.");
            }
            request.setResultKey(resultKey);
            return pacsResultMapper.findByResultKey(resultKey);
        }
        Long resultId = positiveOrNull(request.getResultId());
        return resultId == null ? null : pacsResultMapper.findById(resultId);
    }

    @Override
    @Transactional
    public ResponseMessage<BaseResult> deleteImage(PacsResultImageRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        PacsResultImageRequest safeRequest;
        try {
            safeRequest = normalizeImageRequest(request);
        } catch (IllegalArgumentException validation) {
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        }
        PacsResultImageResponse image = pacsResultMapper.findImage(safeRequest);
        if (image == null) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("Result image not found.", false));
        }
        PacsResultResponse result = pacsResultMapper.findById(image.getResultId());
        try {
            ResultAccess access = authorizeExistingResult(result, httpServletRequest, true);
            validateWritableResult(result, findViewerStateForResult(result), access.claims());
        } catch (ResultAccessException accessError) {
            return resultAccessDenied(accessError);
        }
        pacsResultMapper.deactivateImage(safeRequest);
        deletePhysicalImageQuietly(image.getImagePath());
        return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
    }

    @Override
    public ResponseEntity<Resource> readImage(PacsResultImageRequest request, HttpServletRequest httpServletRequest) {
        PacsResultImageRequest safeRequest;
        try {
            safeRequest = normalizeImageRequest(request);
        } catch (IllegalArgumentException validation) {
            return ResponseEntity.notFound().build();
        }
        PacsResultImageResponse image = pacsResultMapper.findImage(safeRequest);
        if (image == null || !hasText(image.getImagePath())) {
            return ResponseEntity.notFound().build();
        }
        PacsResultResponse result = pacsResultMapper.findById(image.getResultId());
        try {
            authorizeExistingResult(result, httpServletRequest, false);
        } catch (ResultAccessException accessError) {
            return ResponseEntity.status(accessError.statusCode()).build();
        }
        Path path = resolveStoredPath(image.getImagePath());
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path);
        MediaType mediaType = MediaTypeFactory.getMediaType(path.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }

    @Override
    public ResponseEntity<Resource> readHospitalLogo(PacsResultContextRequest request, HttpServletRequest httpServletRequest) {
        PacsResultContextRequest safeRequest = request == null ? new PacsResultContextRequest() : request;
        ResultAccess access;
        try {
            resolveContextPublicKeys(safeRequest);
            access = authorizeContextRequest(safeRequest, httpServletRequest, false);
        } catch (ResultAccessException accessError) {
            return ResponseEntity.status(accessError.statusCode()).build();
        } catch (IllegalArgumentException validation) {
            return ResponseEntity.notFound().build();
        }

        Long hospitalId = firstNonNull(safeRequest.getHospitalId(), access.claims().hospitalId());
        if (hospitalId == null || hospitalId <= 0) {
            return ResponseEntity.notFound().build();
        }
        List<HospitalResponseDetail> hospitals = hospitalMapper.getHospitalById(hospitalId);
        if (hospitals == null || hospitals.isEmpty() || !hasText(hospitals.get(0).getLogoPath())) {
            return ResponseEntity.notFound().build();
        }

        Path path = resolveStoredPath(hospitals.get(0).getLogoPath());
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(path);
        MediaType mediaType = MediaTypeFactory.getMediaType(path.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }

    @Override
    public ResponseMessage<BaseResult> listTemplates(PacsResultTemplateListRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        try {
            resolveTemplatePublicKeys(request);
            authorizeTemplateRequest(request, httpServletRequest, false);
        } catch (ResultAccessException accessError) {
            return resultAccessDenied(accessError);
        } catch (IllegalArgumentException validation) {
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        }
        if (request == null || request.getHospitalId() == null || request.getHospitalId() <= 0 || request.getModalityId() == null || request.getModalityId() <= 0) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("hospitalKey and modalityKey are required.", false));
        }
        List<PacsResultTemplateResponse> templates = pacsResultMapper.listTemplateOptions(request);
        return ResponseMessageUtils.makeResponse(true, messageService.message("Success", templates, true));
    }

    @Override
    public ResponseMessage<BaseResult> findTemplate(String templateKey, PacsResultTemplateListRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        Long templateId;
        try {
            resolveTemplatePublicKeys(request);
            authorizeTemplateRequest(request, httpServletRequest, false);
            templateId = resolveEntityKey(Entity.PACS_RESULT_TEMPLATE, templateKey, null);
            if (templateId == null || templateId <= 0) {
                throw new IllegalArgumentException("Template not found.");
            }
        } catch (ResultAccessException accessError) {
            return resultAccessDenied(accessError);
        } catch (IllegalArgumentException validation) {
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        }
        if (request == null || request.getHospitalId() == null || request.getHospitalId() <= 0 || request.getModalityId() == null || request.getModalityId() <= 0) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("hospitalKey and modalityKey are required.", false));
        }
        PacsResultTemplateResponse template = pacsResultMapper.findTemplateByIdAndScope(templateId, request);
        if (template == null) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("Template not found for this hospital and modality.", false));
        }
        template.setTemplateContent(firstNonBlank(normalizeRichText(template.getTemplateContent()), "<p><br></p>"));
        return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(template), true));
    }

    @Override
    public ResponseMessage<BaseResult> findViewerState(PacsViewerStateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return findViewerStateInternal(request, httpServletRequest, true);
    }

    @Override
    public ResponseMessage<BaseResult> findBrowserViewerState(PacsViewerStateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return findViewerStateInternal(request, httpServletRequest, false);
    }

    private ResponseMessage<BaseResult> findViewerStateInternal(
            PacsViewerStateRequest request,
            HttpServletRequest httpServletRequest,
            boolean requireServerApiKey
    ) {
        try {
            PacsViewerStateRequest safeRequest =
                    prepareViewerStateRequest(request, httpServletRequest, false, requireServerApiKey);
            PacsViewerStateResponse response = pacsResultMapper.findViewerState(safeRequest);
            if (response == null) {
                return ResponseMessageUtils.makeResponse(true, messageService.message("No viewer state found.", List.of(), true));
            }
            ViewerAccessClaims claims = decodeViewerAccess(httpServletRequest);
            if (!viewerStateMatchesAccess(response, claims)) {
                return resultAccessDenied(ResultAccessException.forbidden("Viewer access is not allowed for this saved viewer state."));
            }
            hydrateViewerStateResponse(response);
            response.setCanEdit(canEditViewerState(response, findResultForViewerState(safeRequest), claims));
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(response), true));
        } catch (IllegalArgumentException validation) {
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        } catch (ResultAccessException accessError) {
            return resultAccessDenied(accessError);
        } catch (Exception error) {
            LOGGER.error("Unable to find PACS viewer state: {}", error.toString(), error);
            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to find viewer state.", false));
        }
    }

    @Override
    @Transactional
    public ResponseMessage<BaseResult> saveViewerState(PacsViewerStateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return saveViewerStateInternal(request, httpServletRequest, true);
    }

    @Override
    @Transactional
    public ResponseMessage<BaseResult> saveBrowserViewerState(PacsViewerStateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return saveViewerStateInternal(request, httpServletRequest, false);
    }

    private ResponseMessage<BaseResult> saveViewerStateInternal(
            PacsViewerStateRequest request,
            HttpServletRequest httpServletRequest,
            boolean requireServerApiKey
    ) {
        boolean transactionMutated = false;
        try {
            PacsViewerStateRequest safeRequest =
                    prepareViewerStateRequest(request, httpServletRequest, true, requireServerApiKey);
            normalizeViewerStatePayload(safeRequest);
            ViewerAccessClaims claims = decodeViewerAccess(httpServletRequest);

            pacsResultMapper.lockViewerStateScope(safeRequest);
            PacsViewerStateResponse existing = pacsResultMapper.findViewerState(safeRequest);
            if (existing != null && !viewerStateMatchesAccess(existing, claims)) {
                return resultAccessDenied(ResultAccessException.forbidden("Viewer access is not allowed for this saved viewer state."));
            }
            validateWritableViewerState(existing, findResultForViewerState(safeRequest), claims);
            if (existing == null && hasText(safeRequest.getViewerStateKey())) {
                return ResponseMessageUtils.makeResponse(
                        false,
                        messageService.message("Viewer state not found.", false)
                );
            }
            Long stateId;
            Long userId = claims == null ? null : claims.userId();
            transactionMutated = true;
            if (existing != null) {
                safeRequest.setId(existing.getId());
                pacsResultMapper.updateViewerState(safeRequest, userId);
                stateId = existing.getId();
            } else {
                stateId = pacsResultMapper.insertViewerState(safeRequest, userId);
            }

            safeRequest.setId(stateId);
            PacsViewerStateResponse response = pacsResultMapper.findViewerState(safeRequest);
            if (response == null) {
                throw new IllegalStateException("Saved viewer state could not be reloaded.");
            }
            hydrateViewerStateResponse(response);
            response.setCanEdit(Boolean.TRUE);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(response), true));
        } catch (IllegalArgumentException validation) {
            if (transactionMutated) {
                throw validation;
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        } catch (ResultAccessException accessError) {
            if (transactionMutated) {
                throw accessError;
            }
            return resultAccessDenied(accessError);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to save PACS viewer state.", error);
        }
    }

    @Override
    @Transactional
    public ResponseMessage<BaseResult> deleteViewerState(PacsViewerStateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return deleteViewerStateInternal(request, httpServletRequest, true);
    }

    @Override
    @Transactional
    public ResponseMessage<BaseResult> deleteBrowserViewerState(PacsViewerStateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return deleteViewerStateInternal(request, httpServletRequest, false);
    }

    private ResponseMessage<BaseResult> deleteViewerStateInternal(
            PacsViewerStateRequest request,
            HttpServletRequest httpServletRequest,
            boolean requireServerApiKey
    ) {
        boolean transactionMutated = false;
        try {
            PacsViewerStateRequest safeRequest =
                    prepareViewerStateRequest(request, httpServletRequest, true, requireServerApiKey);
            ViewerAccessClaims claims = decodeViewerAccess(httpServletRequest);
            pacsResultMapper.lockViewerStateScope(safeRequest);
            PacsViewerStateResponse existing = pacsResultMapper.findViewerState(safeRequest);
            if (existing == null) {
                return ResponseMessageUtils.makeResponse(true, messageService.message("No viewer state found.", true));
            }
            if (!viewerStateMatchesAccess(existing, claims)) {
                return resultAccessDenied(ResultAccessException.forbidden("Viewer access is not allowed for this saved viewer state."));
            }
            validateWritableViewerState(existing, findResultForViewerState(safeRequest), claims);
            transactionMutated = true;
            int rows = pacsResultMapper.deactivateViewerState(safeRequest, claims == null ? null : claims.userId());
            return ResponseMessageUtils.makeResponse(true, messageService.message(rows > 0 ? "Success" : "No viewer state found.", true));
        } catch (IllegalArgumentException validation) {
            if (transactionMutated) {
                throw validation;
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        } catch (ResultAccessException accessError) {
            if (transactionMutated) {
                throw accessError;
            }
            return resultAccessDenied(accessError);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to delete PACS viewer state.", error);
        }
    }

    private ResultAccess authorizeSaveRequest(PacsResultSaveRequest request, HttpServletRequest httpServletRequest) {
        ViewerAccessClaims claims = decodeViewerAccess(httpServletRequest);
        if (!ViewerAccessKeyService.canWrite(claims)) {
            throw ResultAccessException.forbidden("This viewer is read-only for PACS result.");
        }
        if (claims.userId() == null || claims.userId() <= 0L) {
            throw ResultAccessException.forbidden("Doctor identity is required to save PACS result.");
        }
        if (!hasServerResultAuth(httpServletRequest, claims)) {
            throw ResultAccessException.unauthorized("Invalid PACS Result proxy API key.");
        }
        if (request != null && !ViewerAccessKeyService.matchesScope(
                claims,
                firstNonNull(request.getHospitalId(), claims.hospitalId()),
                firstNonNull(request.getWorklistId(), claims.worklistId()),
                firstNonNull(request.getStudyId(), claims.studyId()),
                firstNonNull(request.getModalityId(), claims.modalityId()),
                firstNonBlank(request.getStudyInstanceUid(), claims.studyInstanceUid())
        )) {
            throw ResultAccessException.forbidden("Viewer access is not allowed for this result.");
        }
        return new ResultAccess(claims);
    }

    private void resolveSavePublicKeys(PacsResultSaveRequest request) {
        if (request == null) {
            return;
        }
        request.setHospitalId(resolveScopedEntityKey(Entity.HOSPITAL, request.getHospitalKey(), null));
        request.setModalityId(resolveScopedEntityKey(Entity.MODALITY, request.getModalityKey(), null));
        request.setWorklistId(resolveScopedEntityKey(Entity.WORKLIST, request.getWorklistKey(), null));
        request.setStudyId(resolveScopedEntityKey(Entity.STUDY, request.getStudyKey(), null));
        request.setPatientId(resolveScopedEntityKey(Entity.PATIENT, request.getPatientKey(), null));
        request.setTemplateId(resolveEntityKey(Entity.PACS_RESULT_TEMPLATE, request.getTemplateKey(), null));
    }

    private void resolveContextPublicKeys(PacsResultContextRequest request) {
        if (request == null) {
            return;
        }
        request.setHospitalId(resolveScopedEntityKey(Entity.HOSPITAL, request.getHospitalKey(), null));
        request.setModalityId(resolveScopedEntityKey(Entity.MODALITY, request.getModalityKey(), null));
        request.setWorklistId(resolveScopedEntityKey(Entity.WORKLIST, request.getWorklistKey(), null));
        request.setStudyId(resolveScopedEntityKey(Entity.STUDY, request.getStudyKey(), null));
    }

    private void resolveFindByWorklistPublicKeys(PacsResultFindByWorklistRequest request) {
        if (request == null) {
            return;
        }
        request.setHospitalId(resolveScopedEntityKey(Entity.HOSPITAL, request.getHospitalKey(), null));
        request.setWorklistId(resolveScopedEntityKey(Entity.WORKLIST, request.getWorklistKey(), null));
    }

    private void resolveFindByStudyPublicKeys(PacsResultFindByStudyRequest request) {
        if (request == null) {
            return;
        }
        request.setHospitalId(resolveScopedEntityKey(Entity.HOSPITAL, request.getHospitalKey(), null));
        request.setModalityId(resolveScopedEntityKey(Entity.MODALITY, request.getModalityKey(), null));
        Long studyId = resolveEntityKey(Entity.STUDY, request.getStudyKey(), null);
        if (studyId != null) {
            request.setStudyId(String.valueOf(studyId));
        } else if (!hasText(request.getStudyInstanceUid()) && hasText(request.getStudyKey())) {
            request.setStudyInstanceUid(request.getStudyKey().trim());
        } else if (hasText(request.getStudyKey())) {
            throw ResultAccessException.forbidden("Viewer access scope is invalid.");
        }
    }

    private void resolveTemplatePublicKeys(PacsResultTemplateListRequest request) {
        if (request == null) {
            return;
        }
        request.setHospitalId(resolveScopedEntityKey(Entity.HOSPITAL, request.getHospitalKey(), null));
        request.setModalityId(resolveScopedEntityKey(Entity.MODALITY, request.getModalityKey(), null));
    }

    private PacsViewerStateRequest prepareViewerStateRequest(
            PacsViewerStateRequest request,
            HttpServletRequest httpServletRequest,
            boolean write,
            boolean requireServerApiKey
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required.");
        }
        resolveViewerStatePublicKeys(request);
        ResultAccess access =
                authorizeViewerStateRequest(request, httpServletRequest, write, requireServerApiKey);
        applyAccessClaims(request, access.claims());
        normalizeViewerStateReference(request);
        enrichViewerStateContext(request);
        validateViewerStateReference(request);
        validateViewerStateScope(access.claims(), request);
        return request;
    }

    private void resolveViewerStatePublicKeys(PacsViewerStateRequest request) {
        if (request == null) {
            return;
        }
        request.setHospitalId(resolveScopedEntityKey(Entity.HOSPITAL, request.getHospitalKey(), null));
        request.setModalityId(resolveScopedEntityKey(Entity.MODALITY, request.getModalityKey(), null));
        request.setStudyId(resolveScopedEntityKey(Entity.STUDY, request.getStudyKey(), null));
        request.setWorklistId(resolveScopedEntityKey(Entity.WORKLIST, request.getWorklistKey(), null));
        request.setPatientId(resolveScopedEntityKey(Entity.PATIENT, request.getPatientKey(), null));
        String viewerStateKey = trimToNull(request.getViewerStateKey());
        if (hasText(viewerStateKey)) {
            try {
                UUID.fromString(viewerStateKey);
            } catch (IllegalArgumentException ex) {
                LOGGER.debug("Invalid UUID format for viewerStateKey: {}", ex.getMessage());
                throw new IllegalArgumentException("Invalid viewer state reference.");
            }
            request.setViewerStateKey(viewerStateKey);
        }
    }

    private ResultAccess authorizeViewerStateRequest(
            PacsViewerStateRequest request,
            HttpServletRequest httpServletRequest,
            boolean write,
            boolean requireServerApiKey
    ) {
        ViewerAccessClaims claims = authorizeReadWrite(httpServletRequest, write, requireServerApiKey);
        if (write && (claims.userId() == null || claims.userId() <= 0L)) {
            throw ResultAccessException.forbidden("Doctor identity is required to save viewer state.");
        }
        if (request != null && !ViewerAccessKeyService.matchesScope(
                claims,
                firstNonNull(request.getHospitalId(), claims.hospitalId()),
                firstNonNull(request.getWorklistId(), claims.worklistId()),
                firstNonNull(request.getStudyId(), claims.studyId()),
                firstNonNull(request.getModalityId(), claims.modalityId()),
                firstNonBlank(request.getStudyInstanceUid(), claims.studyInstanceUid())
        )) {
            throw ResultAccessException.forbidden("Viewer access is not allowed for this viewer state.");
        }
        return new ResultAccess(claims);
    }

    private Long resolveEntityKey(Entity entity, String publicKey, Long fallbackId) {
        if (publicEntityKeyResolver == null) {
            return positiveOrNull(fallbackId);
        }
        return publicEntityKeyResolver.resolve(entity, publicKey, fallbackId);
    }

    private Long resolveScopedEntityKey(Entity entity, String publicKey, Long fallbackId) {
        Long resolved;
        try {
            resolved = resolveEntityKey(entity, publicKey, fallbackId);
        } catch (RuntimeException error) {
            if (hasText(publicKey)) {
                throw ResultAccessException.forbidden("Viewer access scope is invalid.");
            }
            throw error;
        }
        if (hasText(publicKey) && positiveOrNull(resolved) == null) {
            throw ResultAccessException.forbidden("Viewer access scope is invalid.");
        }
        return resolved;
    }

    private ResultAccess authorizeFindByWorklistRequest(PacsResultFindByWorklistRequest request, HttpServletRequest httpServletRequest, boolean write) {
        ViewerAccessClaims claims = authorizeReadWrite(httpServletRequest, write);
        Long hospitalId = firstNonNull(request == null ? null : request.getHospitalId(), claims.hospitalId());
        Long worklistId = firstNonNull(request == null ? null : request.getWorklistId(), claims.worklistId());
        if (!ViewerAccessKeyService.matchesScope(claims, hospitalId, worklistId, null, null, null)) {
            throw ResultAccessException.forbidden("Viewer access is not allowed for this worklist.");
        }
        return new ResultAccess(claims);
    }

    private ResultAccess authorizeFindByStudyRequest(PacsResultFindByStudyRequest request, HttpServletRequest httpServletRequest, boolean write) {
        ViewerAccessClaims claims = authorizeReadWrite(httpServletRequest, write);
        Long hospitalId = firstNonNull(request == null ? null : request.getHospitalId(), claims.hospitalId());
        Long modalityId = firstNonNull(request == null ? null : request.getModalityId(), claims.modalityId());
        Long studyId = null;
        String studyInstanceUid = null;
        if (request != null && hasText(firstNonBlank(request.getStudyId(), request.getStudyInstanceUid(), ""))) {
            String studyKey = firstNonBlank(request.getStudyId(), request.getStudyInstanceUid(), "").trim();
            if (studyKey.matches("^[0-9]+$")) {
                studyId = Long.valueOf(studyKey);
            } else {
                studyInstanceUid = studyKey;
            }
        }
        if (!ViewerAccessKeyService.matchesScope(claims, hospitalId, null, studyId, modalityId, studyInstanceUid)) {
            throw ResultAccessException.forbidden("Viewer access is not allowed for this study.");
        }
        return new ResultAccess(claims);
    }

    private ResultAccess authorizeContextRequest(PacsResultContextRequest request, HttpServletRequest httpServletRequest, boolean write) {
        ViewerAccessClaims claims = authorizeReadWrite(httpServletRequest, write);
        if (request != null && !ViewerAccessKeyService.matchesScope(
                claims,
                firstNonNull(request.getHospitalId(), claims.hospitalId()),
                firstNonNull(request.getWorklistId(), claims.worklistId()),
                firstNonNull(request.getStudyId(), claims.studyId()),
                firstNonNull(request.getModalityId(), claims.modalityId()),
                firstNonBlank(request.getStudyInstanceUid(), claims.studyInstanceUid())
        )) {
            throw ResultAccessException.forbidden("Viewer access is not allowed for this study.");
        }
        return new ResultAccess(claims);
    }

    private ResultAccess authorizeTemplateRequest(PacsResultTemplateListRequest request, HttpServletRequest httpServletRequest, boolean write) {
        ViewerAccessClaims claims = authorizeReadWrite(httpServletRequest, write);
        if (request != null && !ViewerAccessKeyService.matchesScope(
                claims,
                firstNonNull(request.getHospitalId(), claims.hospitalId()),
                null,
                null,
                firstNonNull(request.getModalityId(), claims.modalityId()),
                null
        )) {
            throw ResultAccessException.forbidden("Viewer access is not allowed for these templates.");
        }
        return new ResultAccess(claims);
    }

    private ResultAccess authorizeExistingResult(PacsResultResponse result, HttpServletRequest httpServletRequest, boolean write) {
        ViewerAccessClaims claims = authorizeReadWrite(httpServletRequest, write);
        if (result == null) {
            throw ResultAccessException.forbidden("PACS result not found.");
        }
        if (!resultMatchesAccess(result, claims)) {
            throw ResultAccessException.forbidden("Viewer access is not allowed for this result.");
        }
        return new ResultAccess(claims);
    }

    private ViewerAccessClaims authorizeReadWrite(HttpServletRequest httpServletRequest, boolean write) {
        return authorizeReadWrite(httpServletRequest, write, true);
    }

    private ViewerAccessClaims authorizeReadWrite(
            HttpServletRequest httpServletRequest,
            boolean write,
            boolean requireServerApiKey
    ) {
        ViewerAccessClaims claims = decodeViewerAccess(httpServletRequest);
        if (write && !ViewerAccessKeyService.canWrite(claims)) {
            throw ResultAccessException.forbidden("This viewer is read-only for PACS result.");
        }
        if (!write && !ViewerAccessKeyService.canRead(claims)) {
            throw ResultAccessException.forbidden("Viewer access is not allowed for PACS result.");
        }
        if (requireServerApiKey && !hasServerResultAuth(httpServletRequest, claims)) {
            throw ResultAccessException.unauthorized("Invalid PACS Result proxy API key.");
        }
        return claims;
    }

    private ViewerAccessClaims decodeViewerAccess(HttpServletRequest request) {
        String rawToken = firstNonBlank(
                request == null ? null : request.getHeader("X-PACS-VIEWER-ACCESS"),
                request == null ? null : request.getHeader("X-PACS-VIEWER-API-KEY"),
                request == null ? null : request.getParameter("viewerAccessToken"),
                request == null ? null : request.getParameter("viewerApiKey")
        );
        if (!hasText(rawToken)) {
            throw ResultAccessException.unauthorized("Viewer access token is required.");
        }
        try {
            return viewerAccessKeyService.decode(rawToken);
        } catch (Exception ex) {
            LOGGER.debug("Viewer access token decode failed: {}", ex.getMessage());
            throw ResultAccessException.unauthorized("Viewer access token is invalid or expired.");
        }
    }

    private boolean hasServerResultAuth(HttpServletRequest request, ViewerAccessClaims claims) {
        String provided = firstNonBlank(
                request == null ? null : request.getHeader("X-PACS-RESULT-API-KEY"),
                request == null ? null : request.getHeader("X-API-KEY")
        );
        if (!hasText(provided)) {
            return false;
        }
        if (staticAuthEnabled && hasText(configuredApiKey)
                && constantTimeEquals(configuredApiKey.trim(), provided.trim())) {
            return true;
        }
        if (claims == null || claims.hospitalId() == null || claims.hospitalId() <= 0L) {
            return false;
        }
        List<HospitalDicomServerResponse> candidates = new ArrayList<>();
        if (claims.worklistId() != null && claims.worklistId() > 0L) {
            HospitalDicomServerResponse worklistServer =
                    dicomServerMapper.findActiveDicomServerByWorklist(claims.hospitalId(), claims.worklistId());
            if (worklistServer != null) {
                candidates.add(worklistServer);
            }
        }
        List<HospitalDicomServerResponse> hospitalServers =
                dicomServerMapper.listActiveDicomServersByHospital(claims.hospitalId());
        if (hospitalServers != null) {
            candidates.addAll(hospitalServers);
        }
        for (HospitalDicomServerResponse server : candidates) {
            String hash = server == null ? null : server.getPacsResultApiKeyHash();
            if (matchesStoredSecret(provided, hash)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesStoredSecret(String provided, String storedHash) {
        if (!hasText(provided) || !hasText(storedHash)) {
            return false;
        }
        try {
            return passwordEncoder != null && passwordEncoder.matches(provided.trim(), storedHash.trim());
        } catch (Exception ex) {
            LOGGER.debug("Password hash comparison failed: {}", ex.getMessage());
            return false;
        }
    }

    private static void applyAccessClaims(PacsResultSaveRequest request, ViewerAccessClaims claims) {
        if (request == null || claims == null) {
            return;
        }
        request.setHospitalId(firstNonNull(request.getHospitalId(), claims.hospitalId()));
        request.setWorklistId(firstNonNull(request.getWorklistId(), claims.worklistId()));
        request.setStudyId(firstNonNull(request.getStudyId(), claims.studyId()));
        request.setModalityId(firstNonNull(request.getModalityId(), claims.modalityId()));
        request.setStudyInstanceUid(firstNonBlank(request.getStudyInstanceUid(), claims.studyInstanceUid()));
    }

    private static void applyAccessClaims(PacsResultContextRequest request, ViewerAccessClaims claims) {
        if (request == null || claims == null) {
            return;
        }
        request.setHospitalId(firstNonNull(request.getHospitalId(), claims.hospitalId()));
        request.setWorklistId(firstNonNull(request.getWorklistId(), claims.worklistId()));
        request.setStudyId(firstNonNull(request.getStudyId(), claims.studyId()));
        request.setModalityId(firstNonNull(request.getModalityId(), claims.modalityId()));
        request.setStudyInstanceUid(firstNonBlank(request.getStudyInstanceUid(), claims.studyInstanceUid()));
    }

    private static void applyAccessClaims(PacsViewerStateRequest request, ViewerAccessClaims claims) {
        if (request == null || claims == null) {
            return;
        }
        request.setHospitalId(firstNonNull(request.getHospitalId(), claims.hospitalId()));
        request.setWorklistId(firstNonNull(request.getWorklistId(), claims.worklistId()));
        request.setStudyId(firstNonNull(request.getStudyId(), claims.studyId()));
        request.setModalityId(firstNonNull(request.getModalityId(), claims.modalityId()));
        request.setStudyInstanceUid(firstNonBlank(request.getStudyInstanceUid(), claims.studyInstanceUid()));
    }

    private static void validateRequestScope(ViewerAccessClaims claims, PacsResultSaveRequest request) {
        if (!ViewerAccessKeyService.matchesScope(
                claims,
                request == null ? null : request.getHospitalId(),
                request == null ? null : request.getWorklistId(),
                request == null ? null : request.getStudyId(),
                request == null ? null : request.getModalityId(),
                request == null ? null : request.getStudyInstanceUid()
        )) {
            throw ResultAccessException.forbidden("Viewer access is not allowed for this result.");
        }
    }

    private static boolean contextMatchesAccess(PacsResultContextResponse context, ViewerAccessClaims claims) {
        return ViewerAccessKeyService.matchesScope(
                claims,
                context == null ? null : context.getHospitalId(),
                context == null ? null : context.getWorklistId(),
                context == null ? null : context.getStudyId(),
                context == null ? null : context.getModalityId(),
                context == null ? null : context.getStudyInstanceUid()
        );
    }

    private static boolean resultMatchesAccess(PacsResultResponse result, ViewerAccessClaims claims) {
        return ViewerAccessKeyService.matchesScope(
                claims,
                result == null ? null : result.getHospitalId(),
                result == null ? null : result.getWorklistId(),
                result == null ? null : result.getStudyId(),
                result == null ? null : result.getModalityId(),
                result == null ? null : result.getStudyInstanceUid()
        );
    }

    private static boolean viewerStateMatchesAccess(PacsViewerStateResponse state, ViewerAccessClaims claims) {
        return ViewerAccessKeyService.matchesScope(
                claims,
                state == null ? null : state.getHospitalId(),
                state == null ? null : state.getWorklistId(),
                state == null ? null : state.getStudyId(),
                state == null ? null : state.getModalityId(),
                state == null ? null : state.getStudyInstanceUid()
        );
    }

    private PacsViewerStateResponse findViewerStateForResult(PacsResultSaveRequest request) {
        if (request == null || request.getHospitalId() == null || request.getHospitalId() <= 0L) {
            return null;
        }
        PacsViewerStateRequest stateRequest = new PacsViewerStateRequest();
        stateRequest.setHospitalId(request.getHospitalId());
        stateRequest.setModalityId(request.getModalityId());
        stateRequest.setStudyId(request.getStudyId());
        stateRequest.setWorklistId(request.getWorklistId());
        stateRequest.setStudyInstanceUid(request.getStudyInstanceUid());
        stateRequest.setAccessionNumber(request.getAccessionNumber());
        stateRequest.setStateType(DEFAULT_VIEWER_STATE_TYPE);
        return hasViewerStateReference(stateRequest) ? pacsResultMapper.findViewerState(stateRequest) : null;
    }

    private PacsViewerStateResponse findViewerStateForResult(PacsResultResponse result) {
        if (result == null || result.getHospitalId() == null || result.getHospitalId() <= 0L) {
            return null;
        }
        PacsViewerStateRequest stateRequest = new PacsViewerStateRequest();
        stateRequest.setHospitalId(result.getHospitalId());
        stateRequest.setModalityId(result.getModalityId());
        stateRequest.setStudyId(result.getStudyId());
        stateRequest.setWorklistId(result.getWorklistId());
        stateRequest.setStudyInstanceUid(result.getStudyInstanceUid());
        stateRequest.setAccessionNumber(result.getAccessionNumber());
        stateRequest.setStateType(DEFAULT_VIEWER_STATE_TYPE);
        return hasViewerStateReference(stateRequest) ? pacsResultMapper.findViewerState(stateRequest) : null;
    }

    private PacsResultResponse findResultForViewerState(PacsViewerStateRequest request) {
        if (request == null || request.getHospitalId() == null || request.getHospitalId() <= 0L) {
            return null;
        }
        if (request.getWorklistId() != null && request.getWorklistId() > 0L) {
            PacsResultFindByWorklistRequest resultRequest = new PacsResultFindByWorklistRequest();
            resultRequest.setHospitalId(request.getHospitalId());
            resultRequest.setWorklistId(request.getWorklistId());
            return pacsResultMapper.findByWorklist(resultRequest);
        }
        if (request.getStudyId() != null && request.getStudyId() > 0L
                && request.getModalityId() != null && request.getModalityId() > 0L) {
            return pacsResultMapper.findByStudyId(
                    request.getHospitalId(),
                    request.getModalityId(),
                    request.getStudyId()
            );
        }
        if (hasText(request.getStudyInstanceUid())
                && request.getModalityId() != null && request.getModalityId() > 0L) {
            return pacsResultMapper.findByStudyInstanceUid(
                    request.getHospitalId(),
                    request.getModalityId(),
                    request.getStudyInstanceUid()
            );
        }
        return null;
    }

    private static boolean hasViewerStateReference(PacsViewerStateRequest request) {
        return request != null
                && ((request.getWorklistId() != null && request.getWorklistId() > 0L)
                || (request.getStudyId() != null && request.getStudyId() > 0L)
                || hasText(request.getStudyInstanceUid())
                || hasText(request.getAccessionNumber()));
    }

    private static void validateWritableResult(
            PacsResultResponse existing,
            PacsViewerStateResponse existingState,
            ViewerAccessClaims claims
    ) {
        if (!ViewerAccessKeyService.canWrite(claims)) {
            throw ResultAccessException.forbidden("This viewer is read-only for PACS result.");
        }
        if (claims == null || claims.userId() == null || claims.userId() <= 0L) {
            throw ResultAccessException.forbidden("Doctor identity is required to save PACS result.");
        }
        Long ownerId = viewerOwnerId(existing, existingState);
        if (ownerId != null && !claims.userId().equals(ownerId)) {
            throw ResultAccessException.forbidden("Only the reporting doctor can edit this PACS result.");
        }
    }

    private static void validateWritableViewerState(
            PacsViewerStateResponse existing,
            PacsResultResponse existingResult,
            ViewerAccessClaims claims
    ) {
        if (!ViewerAccessKeyService.canWrite(claims)) {
            throw ResultAccessException.forbidden("This viewer is read-only for labels and annotations.");
        }
        if (claims == null || claims.userId() == null || claims.userId() <= 0L) {
            throw ResultAccessException.forbidden("Doctor identity is required to save labels and annotations.");
        }
        Long ownerId = viewerOwnerId(existingResult, existing);
        if (ownerId != null && !claims.userId().equals(ownerId)) {
            throw ResultAccessException.forbidden(
                    "Only the clinician who created these labels and annotations can edit or delete them."
            );
        }
    }

    private static boolean canEditViewerState(
            PacsViewerStateResponse existing,
            PacsResultResponse existingResult,
            ViewerAccessClaims claims
    ) {
        Long ownerId = viewerOwnerId(existingResult, existing);
        return existing != null
                && ViewerAccessKeyService.canWrite(claims)
                && claims != null
                && claims.userId() != null
                && ownerId != null
                && claims.userId().equals(ownerId);
    }

    private static Long viewerOwnerId(PacsResultResponse result, PacsViewerStateResponse state) {
        Long resultOwner = positiveUserId(result == null ? null : result.getCreatedBy());
        Long stateOwner = positiveUserId(state == null ? null : state.getCreatedBy());
        return firstNonNull(resultOwner, stateOwner);
    }

    private static Long positiveUserId(Long userId) {
        return userId != null && userId > 0L ? userId : null;
    }

    private ResponseMessage<BaseResult> resultAccessDenied(ResultAccessException accessError) {
        return ResponseMessageUtils.makeResponse(
                false,
                accessError.statusCode(),
                accessError.statusCode() == 401 ? "Unauthorized" : "Forbidden",
                accessError.getMessage()
        );
    }

    private PacsResultContextResponse resolveResultContext(PacsResultContextRequest request) {
        Long modalityId = positiveOrNull(request.getModalityId());
        PacsResultContextResponse context = null;

        if (positiveOrNull(request.getWorklistId()) != null) {
            context = pacsResultMapper.findContextByWorklistId(request);
            if (hasResultContext(context, modalityId)) {
                return context;
            }
        }
        if (positiveOrNull(request.getStudyId()) != null) {
            context = pacsResultMapper.findContextByStudyId(request);
            if (hasResultContext(context, modalityId)) {
                return context;
            }
        }
        if (hasText(request.getStudyInstanceUid())) {
            context = pacsResultMapper.findContextByStudyInstanceUid(request);
            if (hasResultContext(context, modalityId)) {
                return context;
            }
        }
        if (hasText(request.getAccessionNumber())) {
            context = pacsResultMapper.findContextByAccessionNumber(request);
            if (hasResultContext(context, modalityId)) {
                return context;
            }
        }
        return null;
    }

    private static boolean hasResultContext(PacsResultContextResponse context, Long requestedModalityId) {
        if (context == null || context.getHospitalId() == null || context.getHospitalId() <= 0) {
            return false;
        }
        if (context.getModalityId() == null || context.getModalityId() <= 0) {
            return false;
        }
        return requestedModalityId == null || requestedModalityId.equals(context.getModalityId());
    }

    private void normalizeViewerStateReference(PacsViewerStateRequest request) {
        request.setStudyInstanceUid(trimToNull(request.getStudyInstanceUid()));
        request.setAccessionNumber(trimToNull(request.getAccessionNumber()));
        request.setPatientCode(trimToNull(request.getPatientCode()));
        validateViewerStateReferenceLength("studyInstanceUid", request.getStudyInstanceUid(), 255);
        validateViewerStateReferenceLength("accessionNumber", request.getAccessionNumber(), 255);
        validateViewerStateReferenceLength("patientCode", request.getPatientCode(), 255);
        String stateType = trimToNull(request.getStateType());
        if (!hasText(stateType)) {
            stateType = DEFAULT_VIEWER_STATE_TYPE;
        }
        stateType = stateType.toUpperCase(Locale.ROOT);
        if (stateType.length() > 64 || !stateType.matches("^[A-Z0-9][A-Z0-9_-]*$")) {
            throw new IllegalArgumentException("stateType must use 1-64 letters, numbers, underscores, or hyphens.");
        }
        request.setStateType(stateType);
        Integer schemaVersion = request.getSchemaVersion();
        if (schemaVersion == null) {
            schemaVersion = DEFAULT_VIEWER_STATE_SCHEMA_VERSION;
        }
        if (schemaVersion <= 0 || schemaVersion > MAX_VIEWER_STATE_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schemaVersion must be between 1 and 1000.");
        }
        request.setSchemaVersion(schemaVersion);
    }

    private static void validateViewerStateReferenceLength(String field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long.");
        }
    }

    private void enrichViewerStateContext(PacsViewerStateRequest request) {
        PacsResultContextRequest contextRequest = new PacsResultContextRequest();
        contextRequest.setHospitalId(request.getHospitalId());
        contextRequest.setModalityId(request.getModalityId());
        contextRequest.setStudyId(request.getStudyId());
        contextRequest.setWorklistId(request.getWorklistId());
        contextRequest.setStudyInstanceUid(request.getStudyInstanceUid());
        contextRequest.setAccessionNumber(request.getAccessionNumber());
        contextRequest.setPatientCode(request.getPatientCode());
        PacsResultContextResponse context = resolveResultContext(contextRequest);
        if (context == null) {
            return;
        }
        request.setHospitalId(firstNonNull(request.getHospitalId(), context.getHospitalId()));
        request.setModalityId(firstNonNull(request.getModalityId(), context.getModalityId()));
        request.setStudyId(firstNonNull(request.getStudyId(), context.getStudyId()));
        request.setWorklistId(firstNonNull(request.getWorklistId(), context.getWorklistId()));
        request.setPatientId(firstNonNull(request.getPatientId(), context.getPatientId()));
        request.setStudyInstanceUid(firstNonBlank(request.getStudyInstanceUid(), context.getStudyInstanceUid()));
        request.setAccessionNumber(firstNonBlank(request.getAccessionNumber(), context.getAccessionNumber()));
        request.setPatientCode(firstNonBlank(request.getPatientCode(), context.getPatientCode()));
    }

    private void validateViewerStateReference(PacsViewerStateRequest request) {
        if (request.getHospitalId() == null || request.getHospitalId() <= 0) {
            throw new IllegalArgumentException("hospitalKey is required.");
        }
        if (request.getWorklistId() == null
                && request.getStudyId() == null
                && !hasText(request.getStudyInstanceUid())
                && !hasText(request.getAccessionNumber())) {
            throw new IllegalArgumentException("worklistKey, studyKey, studyInstanceUid, or accessionNumber is required.");
        }
    }

    private static void validateViewerStateScope(ViewerAccessClaims claims, PacsViewerStateRequest request) {
        if (!ViewerAccessKeyService.matchesScope(
                claims,
                request == null ? null : request.getHospitalId(),
                request == null ? null : request.getWorklistId(),
                request == null ? null : request.getStudyId(),
                request == null ? null : request.getModalityId(),
                request == null ? null : request.getStudyInstanceUid()
        )) {
            throw ResultAccessException.forbidden("Viewer access is not allowed for this viewer state.");
        }
    }

    private void normalizeViewerStatePayload(PacsViewerStateRequest request) {
        validateViewerStatePayloadTypes(request);
        JsonNode segmentations = arrayOrEmpty(request.getSegmentations());
        JsonNode labelmaps = arrayOrEmpty(request.getLabelmapSegmentations());
        JsonNode contours = arrayOrEmpty(request.getContourSegmentations());
        JsonNode surfaces = arrayOrEmpty(request.getSurfaceSegmentations());

        if (segmentations.isEmpty() && (!labelmaps.isEmpty() || !contours.isEmpty() || !surfaces.isEmpty())) {
            segmentations = summarizeViewerSegmentationArrays(labelmaps, contours, surfaces);
        }
        if (!segmentations.isEmpty()) {
            if (labelmaps.isEmpty()) {
                labelmaps = filterViewerSegmentationsByType(segmentations, "LABELMAP");
            }
            if (contours.isEmpty()) {
                contours = filterViewerSegmentationsByType(segmentations, "CONTOUR");
            }
            if (surfaces.isEmpty()) {
                surfaces = filterViewerSegmentationsByType(segmentations, "SURFACE");
            }
        }

        request.setViewerStateJson(jsonToStringOrDefault(objectOrEmpty(request.getViewerState()), "{}"));
        request.setMeasurementsJson(jsonToStringOrDefault(arrayOrEmpty(request.getMeasurements()), "[]"));
        request.setAnnotationsJson(jsonToStringOrDefault(arrayOrEmpty(request.getAnnotations()), "[]"));
        request.setSegmentationsJson(jsonToStringOrDefault(segmentations, "[]"));
        request.setLabelmapSegmentationsJson(jsonToStringOrDefault(labelmaps, "[]"));
        request.setContourSegmentationsJson(jsonToStringOrDefault(contours, "[]"));
        request.setSurfaceSegmentationsJson(jsonToStringOrDefault(surfaces, "[]"));
        request.setAdditionalFindingsJson(jsonToStringOrDefault(arrayOrEmpty(request.getAdditionalFindings()), "[]"));
        request.setPresentationStateJson(jsonToStringOrDefault(objectOrEmpty(request.getPresentationState()), "{}"));
        request.setToolStateJson(jsonToStringOrDefault(objectOrEmpty(request.getToolState()), "{}"));
        request.setMetadataJson(jsonToStringOrDefault(objectOrEmpty(request.getMetadata()), "{}"));

        String[] payloadParts = {
                request.getViewerStateJson(),
                request.getMeasurementsJson(),
                request.getAnnotationsJson(),
                request.getSegmentationsJson(),
                request.getLabelmapSegmentationsJson(),
                request.getContourSegmentationsJson(),
                request.getSurfaceSegmentationsJson(),
                request.getAdditionalFindingsJson(),
                request.getPresentationStateJson(),
                request.getToolStateJson(),
                request.getMetadataJson()
        };
        long totalBytes = viewerStateJsonBytes(payloadParts);
        if (totalBytes > MAX_VIEWER_STATE_JSON_BYTES) {
            throw new IllegalArgumentException("Viewer state is too large.");
        }
        request.setPayloadSizeBytes(totalBytes);
        request.setPayloadSha256(viewerStatePayloadSha256(payloadParts));
    }

    private static void validateViewerStatePayloadTypes(PacsViewerStateRequest request) {
        requireJsonObject("viewerState", request.getViewerState());
        requireJsonArray("measurements", request.getMeasurements());
        requireJsonArray("annotations", request.getAnnotations());
        requireJsonArray("segmentations", request.getSegmentations());
        requireJsonArray("labelmapSegmentations", request.getLabelmapSegmentations());
        requireJsonArray("contourSegmentations", request.getContourSegmentations());
        requireJsonArray("surfaceSegmentations", request.getSurfaceSegmentations());
        requireJsonArray("additionalFindings", request.getAdditionalFindings());
        requireJsonObject("presentationState", request.getPresentationState());
        requireJsonObject("toolState", request.getToolState());
        requireJsonObject("metadata", request.getMetadata());

        ViewerStateJsonBudget budget = new ViewerStateJsonBudget();
        validateJsonComplexity("viewerState", request.getViewerState(), 0, budget);
        validateJsonComplexity("measurements", request.getMeasurements(), 0, budget);
        validateJsonComplexity("annotations", request.getAnnotations(), 0, budget);
        validateJsonComplexity("segmentations", request.getSegmentations(), 0, budget);
        validateJsonComplexity("labelmapSegmentations", request.getLabelmapSegmentations(), 0, budget);
        validateJsonComplexity("contourSegmentations", request.getContourSegmentations(), 0, budget);
        validateJsonComplexity("surfaceSegmentations", request.getSurfaceSegmentations(), 0, budget);
        validateJsonComplexity("additionalFindings", request.getAdditionalFindings(), 0, budget);
        validateJsonComplexity("presentationState", request.getPresentationState(), 0, budget);
        validateJsonComplexity("toolState", request.getToolState(), 0, budget);
        validateJsonComplexity("metadata", request.getMetadata(), 0, budget);
    }

    private static void requireJsonArray(String field, JsonNode value) {
        if (value != null && !value.isNull() && !value.isArray()) {
            throw new IllegalArgumentException(field + " must be a JSON array.");
        }
    }

    private static void requireJsonObject(String field, JsonNode value) {
        if (value != null && !value.isNull() && !value.isObject()) {
            throw new IllegalArgumentException(field + " must be a JSON object.");
        }
    }

    private JsonNode arrayOrEmpty(JsonNode node) {
        return node != null && node.isArray() ? node : objectMapper.createArrayNode();
    }

    private JsonNode objectOrEmpty(JsonNode node) {
        return node != null && node.isObject() ? node : objectMapper.createObjectNode();
    }

    private JsonNode combineViewerSegmentationArrays(JsonNode... arrays) {
        var combined = objectMapper.createArrayNode();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode array : arrays) {
            if (array == null || !array.isArray()) {
                continue;
            }
            for (JsonNode item : array) {
                String key = viewerSegmentationKey(item);
                if (seen.add(key)) {
                    combined.add(item);
                }
            }
        }
        return combined;
    }

    private JsonNode summarizeViewerSegmentationArrays(JsonNode... arrays) {
        var combined = objectMapper.createArrayNode();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode array : arrays) {
            if (array == null || !array.isArray()) {
                continue;
            }
            for (JsonNode item : array) {
                String key = viewerSegmentationKey(item);
                if (!seen.add(key)) {
                    continue;
                }
                if (!item.isObject()) {
                    combined.add(item);
                    continue;
                }
                ObjectNode summary = item.deepCopy();
                summary.remove(List.of(
                        "labelmap",
                        "contour",
                        "surface",
                        "sparseLabelmap",
                        "representationData"
                ));
                combined.add(summary);
            }
        }
        return combined;
    }

    private JsonNode filterViewerSegmentationsByType(JsonNode segmentations, String type) {
        var filtered = objectMapper.createArrayNode();
        if (segmentations == null || !segmentations.isArray()) {
            return filtered;
        }
        for (JsonNode item : segmentations) {
            if (viewerSegmentationHasType(item, type)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private boolean viewerSegmentationHasType(JsonNode item, String type) {
        if (item == null || !item.isObject()) {
            return false;
        }
        String expected = type.toUpperCase(Locale.ROOT);
        JsonNode representationTypes = item.get("representationTypes");
        if (representationTypes != null && representationTypes.isArray()) {
            for (JsonNode representationType : representationTypes) {
                if (expected.equals(representationType.asText("").toUpperCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        if (expected.equals(item.path("type").asText("").toUpperCase(Locale.ROOT))) {
            return true;
        }
        JsonNode representation = item.path("representation");
        if (expected.equals(representation.path("type").asText("").toUpperCase(Locale.ROOT))) {
            return true;
        }
        JsonNode representationData = item.path("representationData");
        if (representationData.isObject() && representationData.has(viewerSegmentationRepresentationDataKey(expected))) {
            return true;
        }
        if ("LABELMAP".equals(expected)) {
            return item.has("sparseLabelmap") || item.path("labelmap").has("sparseLabelmap");
        }
        if ("CONTOUR".equals(expected)) {
            return item.path("contour").has("annotationUIDsBySegment");
        }
        return false;
    }

    private String viewerSegmentationRepresentationDataKey(String type) {
        return switch (type) {
            case "LABELMAP" -> "Labelmap";
            case "CONTOUR" -> "Contour";
            case "SURFACE" -> "Surface";
            default -> type;
        };
    }

    private String viewerSegmentationKey(JsonNode item) {
        if (item == null || item.isNull()) {
            return "null";
        }
        String segmentationId = item.path("segmentationId").asText("");
        String type = item.path("type").asText("");
        if (!hasText(type) && item.path("representation").has("type")) {
            type = item.path("representation").path("type").asText("");
        }
        if (!hasText(type) && item.path("representationTypes").isArray()) {
            List<String> representationTypes = new ArrayList<>();
            item.path("representationTypes").forEach(value ->
                    representationTypes.add(value.asText("").toUpperCase(Locale.ROOT)));
            type = String.join(",", representationTypes);
        }
        if (hasText(segmentationId) || hasText(type)) {
            return segmentationId + ":" + type;
        }
        try {
            return objectMapper.writeValueAsString(item);
        } catch (Exception ignored) {
            return item.toString();
        }
    }

    private long viewerStateJsonBytes(String... values) {
        long total = 0;
        for (String value : values) {
            total += (value == null ? "" : value).getBytes(StandardCharsets.UTF_8).length;
        }
        return total;
    }

    private String viewerStatePayloadSha256(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1E);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash viewer state payload.", ex);
        }
    }

    private static void validateJsonComplexity(
            String field,
            JsonNode node,
            int depth,
            ViewerStateJsonBudget budget
    ) {
        if (node == null || node.isNull()) {
            return;
        }
        if (depth > MAX_VIEWER_STATE_JSON_DEPTH) {
            throw new IllegalArgumentException(field + " exceeds the maximum JSON depth.");
        }
        budget.nodes++;
        if (budget.nodes > MAX_VIEWER_STATE_JSON_NODES) {
            throw new IllegalArgumentException("Viewer state contains too many JSON values.");
        }
        if (node.isArray()) {
            if (node.size() > MAX_VIEWER_STATE_ARRAY_ITEMS) {
                throw new IllegalArgumentException(field + " contains too many items.");
            }
            for (JsonNode child : node) {
                validateJsonComplexity(field, child, depth + 1, budget);
            }
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    validateJsonComplexity(field, entry.getValue(), depth + 1, budget));
        }
    }

    private static final class ViewerStateJsonBudget {
        private long nodes;
    }

    private String jsonToStringOrDefault(JsonNode node, String fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            LOGGER.debug("Unable to serialize viewer state JSON: {}", ex.getMessage());
            throw new IllegalArgumentException("Viewer state JSON is invalid.");
        }
    }

    private void hydrateViewerStateResponse(PacsViewerStateResponse response) {
        if (response == null) {
            return;
        }
        response.setViewerState(readJsonOrDefault(response.getViewerStateJson(), "{}"));
        response.setMeasurements(readJsonOrDefault(response.getMeasurementsJson(), "[]"));
        response.setAnnotations(readJsonOrDefault(response.getAnnotationsJson(), "[]"));
        response.setSegmentations(readJsonOrDefault(response.getSegmentationsJson(), "[]"));
        response.setLabelmapSegmentations(readJsonOrDefault(response.getLabelmapSegmentationsJson(), "[]"));
        response.setContourSegmentations(readJsonOrDefault(response.getContourSegmentationsJson(), "[]"));
        response.setSurfaceSegmentations(readJsonOrDefault(response.getSurfaceSegmentationsJson(), "[]"));
        response.setAdditionalFindings(readJsonOrDefault(response.getAdditionalFindingsJson(), "[]"));
        response.setPresentationState(readJsonOrDefault(response.getPresentationStateJson(), "{}"));
        response.setToolState(readJsonOrDefault(response.getToolStateJson(), "{}"));
        response.setMetadata(readJsonOrDefault(response.getMetadataJson(), "{}"));
    }

    private JsonNode readJsonOrDefault(String value, String fallback) {
        try {
            return objectMapper.readTree(hasText(value) ? value : fallback);
        } catch (Exception ex) {
            LOGGER.debug("Unable to parse stored viewer state JSON: {}", ex.getMessage());
            try {
                return objectMapper.readTree(fallback);
            } catch (Exception ignored) {
                return objectMapper.createObjectNode();
            }
        }
    }

    private PacsResultSaveRequest normalizeAndEnrich(PacsResultSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required.");
        }
        trimFields(request);
        if (request.getResultDate() == null) {
            request.setResultDate(LocalDate.now());
        }
        if (request.getCompleted() == null) {
            request.setCompleted(Boolean.FALSE);
        }

        WorklistDetailRow Worklist = null;
        if (request.getWorklistId() != null && request.getHospitalId() != null) {
            Worklist = WorklistMapper.findWorklistById(request.getHospitalId(), request.getWorklistId());
            if (Worklist == null) {
                throw new IllegalArgumentException("Worklist not found for this hospital.");
            }
            request.setHospitalId(firstNonNull(request.getHospitalId(), Worklist.getHospitalId()));
            request.setModalityId(firstNonNull(request.getModalityId(), Worklist.getModalityId()));
            request.setStudyId(firstNonNull(request.getStudyId(), Worklist.getStudyId()));
            request.setWorklistCode(firstNonBlank(request.getWorklistCode(), Worklist.getDicomServerWorklistId()));
            request.setStudyInstanceUid(firstNonBlank(request.getStudyInstanceUid(), Worklist.getStudyInstanceUid()));
            request.setAccessionNumber(firstNonBlank(request.getAccessionNumber(), Worklist.getAccessionNumber(), Worklist.getVisitCode()));
            request.setPatientId(firstNonNull(request.getPatientId(), Worklist.getPatientId()));
            request.setPatientCode(firstNonBlank(request.getPatientCode(), Worklist.getPatientUid()));
            request.setPatientName(firstNonBlank(request.getPatientName(), Worklist.getPatientName()));
        }

        if (request.getHospitalId() == null && Worklist != null) {
            request.setHospitalId(Worklist.getHospitalId());
        }
        if (request.getStudyId() != null && request.getHospitalId() != null) {
            StudyResponse study = studyMapper.findById(request.getHospitalId(), request.getStudyId());
            if (study != null) {
                request.setStudyInstanceUid(firstNonBlank(request.getStudyInstanceUid(), study.getStudyInstanceUid()));
                request.setAccessionNumber(firstNonBlank(request.getAccessionNumber(), study.getAccessionNumber()));
                request.setPatientId(firstNonNull(request.getPatientId(), study.getPatientId()));
                request.setPatientCode(firstNonBlank(request.getPatientCode(), study.getMrn()));
                request.setPatientName(firstNonBlank(request.getPatientName(), study.getPatientName()));
            }
        }
        if ((request.getStudyId() == null || request.getWorklistId() == null || request.getPatientId() == null)
                && (hasText(request.getStudyInstanceUid()) || hasText(request.getAccessionNumber()))) {
            PacsResultContextRequest contextRequest = new PacsResultContextRequest();
            contextRequest.setHospitalId(request.getHospitalId());
            contextRequest.setModalityId(request.getModalityId());
            contextRequest.setStudyId(request.getStudyId());
            contextRequest.setWorklistId(request.getWorklistId());
            contextRequest.setStudyInstanceUid(request.getStudyInstanceUid());
            contextRequest.setAccessionNumber(request.getAccessionNumber());
            PacsResultContextResponse context = resolveResultContext(contextRequest);
            if (context != null) {
                request.setHospitalId(firstNonNull(request.getHospitalId(), context.getHospitalId()));
                request.setModalityId(firstNonNull(request.getModalityId(), context.getModalityId()));
                request.setStudyId(firstNonNull(request.getStudyId(), context.getStudyId()));
                request.setWorklistId(firstNonNull(request.getWorklistId(), context.getWorklistId()));
                request.setWorklistCode(firstNonBlank(request.getWorklistCode(), context.getWorklistCode()));
                request.setStudyInstanceUid(firstNonBlank(request.getStudyInstanceUid(), context.getStudyInstanceUid()));
                request.setAccessionNumber(firstNonBlank(request.getAccessionNumber(), context.getAccessionNumber()));
                request.setPatientId(firstNonNull(request.getPatientId(), context.getPatientId()));
                request.setPatientCode(firstNonBlank(request.getPatientCode(), context.getPatientCode()));
                request.setPatientName(firstNonBlank(request.getPatientName(), context.getPatientName()));
            }
        }

        if (hasText(request.getResultText())) {
            request.setResultText(normalizeRichText(request.getResultText()));
        } else {
            request.setResultText(null);
        }
        return request;
    }

    private void validateSaveRequest(PacsResultSaveRequest request, List<MultipartFile> images, boolean alreadyHasImages) {
        if (request.getHospitalId() == null || request.getHospitalId() <= 0) {
            throw new IllegalArgumentException("hospitalId is required.");
        }
        if (request.getModalityId() == null || request.getModalityId() <= 0) {
            throw new IllegalArgumentException("modalityId is required.");
        }
        if (request.getStudyId() == null && request.getWorklistId() == null) {
            throw new IllegalArgumentException("Known studyId or worklistId is required.");
        }
        boolean hasIncomingImages = images != null && images.stream().anyMatch(file -> file != null && !file.isEmpty());
        if (!hasMeaningfulResultText(request.getResultText()) && !hasIncomingImages && !alreadyHasImages) {
            throw new IllegalArgumentException("Result text or at least one image is required.");
        }
        Long activeModality = modalityMapper.countActiveModalitiesByIds(List.of(request.getModalityId()));
        if (activeModality == null || activeModality <= 0) {
            throw new IllegalArgumentException("Modality not found or inactive.");
        }
        Long hospitalModality = modalityMapper.countActiveHospitalModality(request.getHospitalId(), request.getModalityId());
        if (hospitalModality == null || hospitalModality <= 0) {
            throw new IllegalArgumentException("Modality is not assigned to this hospital.");
        }
    }

    private void updateStudyStatusIfPossible(PacsResultSaveRequest request) {
        if (request.getStudyId() == null) {
            return;
        }
        StudyStatus status = Boolean.TRUE.equals(request.getCompleted())
                ? StudyStatus.COMPLETED
                : StudyStatus.IMAGE_RECEIVED;
        studyMapper.updateStatusById(request.getHospitalId(), request.getStudyId(), status.code());
    }

    private PacsResultResponse findExistingForUpdate(PacsResultSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Result reference is required.");
        }
        String resultKey = trimToNull(request.getResultKey());
        if (hasText(resultKey)) {
            try {
                UUID.fromString(resultKey);
            } catch (IllegalArgumentException ex) {
                LOGGER.debug("Invalid UUID format for result reference: {}", ex.getMessage());
                throw new IllegalArgumentException("Invalid result reference.");
            }
            request.setResultKey(resultKey);
            request.setId(null);
            return pacsResultMapper.findByResultKey(resultKey);
        }

        Long resultId = positiveOrNull(request.getId());
        if (resultId == null) {
            throw new IllegalArgumentException("Result reference is required.");
        }
        request.setId(resultId);
        return pacsResultMapper.findById(resultId);
    }

    private PacsResultImageRequest normalizeImageRequest(PacsResultImageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Image reference is required.");
        }
        String imageKey = trimToNull(request.getImageKey());
        if (hasText(imageKey)) {
            try {
                UUID.fromString(imageKey);
            } catch (IllegalArgumentException ex) {
                LOGGER.debug("Invalid UUID format for image reference: {}", ex.getMessage());
                throw new IllegalArgumentException("Invalid image reference.");
            }
            request.setImageKey(imageKey);
            request.setImageId(null);
            return request;
        }

        Long imageId = positiveOrNull(request.getImageId());
        if (imageId == null) {
            throw new IllegalArgumentException("Image reference is required.");
        }
        request.setImageId(imageId);
        return request;
    }

    private PacsResultResponse loadResult(Long resultId) {
        PacsResultResponse result = pacsResultMapper.findById(resultId);
        normalizeResultResponse(result);
        attachImages(result);
        return result;
    }

    private void normalizeResultResponse(PacsResultResponse result) {
        if (result == null) {
            return;
        }
        result.setResultText(firstNonBlank(normalizeRichText(result.getResultText()), "<p><br></p>"));
    }

    private void attachImages(PacsResultResponse result) {
        if (result == null) {
            return;
        }
        List<PacsResultImageResponse> images = pacsResultMapper.listImages(result.getId());
        for (PacsResultImageResponse image : images) {
            image.setImageUrl(buildImageUrl(image.getImageKey()));
        }
        result.setImages(images);
    }

    private void storeImages(Long resultId, Long hospitalId, Long modalityId, List<MultipartFile> images) throws IOException {
        if (images == null || images.isEmpty()) {
            return;
        }
        String hospitalSegment = resolveHospitalSegment(hospitalId);
        String modalityCode = resolveModalityCode(modalityId);
        int sortOrder = pacsResultMapper.nextImageSortOrder(resultId);
        List<StoredResultImage> storedImages = new ArrayList<>();
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                continue;
            }
            storedImages.add(storeOneImage(hospitalSegment, modalityCode, resultId, image));
        }
        for (StoredResultImage image : storedImages) {
            pacsResultMapper.insertImage(
                    resultId,
                    hospitalId,
                    modalityId,
                    image.storedPath(),
                    image.originalFileName(),
                    image.fileType(),
                    image.fileSize(),
                    sortOrder++
            );
        }
    }

    private StoredResultImage storeOneImage(String hospitalSegment, String modalityCode, Long resultId, MultipartFile file) throws IOException {
        if (file.getSize() > maxImageBytes) {
            throw new IllegalArgumentException("Image file is too large.");
        }
        String originalFilename = sanitizeOriginalFilename(file.getOriginalFilename());
        byte[] bytes = file.getBytes();
        DetectedImageType detectedType = detectImageType(bytes);
        if (detectedType == null || !ALLOWED_IMAGE_EXTENSIONS.contains(detectedType.extension())) {
            throw new IllegalArgumentException("Only valid jpg, jpeg, png, and webp result images are allowed.");
        }

        String fileName = buildStoredFileName(resultId, originalFilename, detectedType.extension());
        String safeHospitalSegment = safePathSegment(hospitalSegment, "HOSPITAL");
        String safeModalitySegment = safePathSegment(modalityCode, "MODALITY");
        Path directory = resolveUploadDirectory()
                .resolve(safeHospitalSegment)
                .resolve(safeModalitySegment)
                .normalize();
        Files.createDirectories(directory);
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(resolveUploadDirectory().normalize())) {
            throw new IllegalArgumentException("Invalid result image path.");
        }
        Files.write(target, bytes);

        String storedPath = "/"
                + safeHospitalSegment
                + "/" + safeModalitySegment
                + "/" + fileName;
        String fileType = detectedType.mimeType();
        return new StoredResultImage(storedPath, originalFilename, fileType, file.getSize());
    }

    private String resolveHospitalSegment(Long hospitalId) {
        if (hospitalId == null || hospitalId <= 0) {
            return "HOSPITAL";
        }
        if (hospitalMapper == null) {
            return "HOSPITAL_" + hospitalId;
        }
        List<HospitalResponseDetail> hospitals = hospitalMapper.getHospitalById(hospitalId);
        if (hospitals == null || hospitals.isEmpty()) {
            return "HOSPITAL_" + hospitalId;
        }
        HospitalResponseDetail hospital = hospitals.get(0);
        return firstNonBlank(
                hospital.getAbbr(),
                hospital.getCode(),
                hospital.getHospitalName(),
                "HOSPITAL_" + hospitalId
        );
    }

    private String resolveModalityCode(Long modalityId) {
        List<ModalityResponse> modalities = modalityMapper.getModalityById(modalityId);
        if (modalities == null || modalities.isEmpty()) {
            return "MODALITY";
        }
        ModalityResponse modality = modalities.get(0);
        return firstNonBlank(modality.getAbbr(), modality.getName(), "MODALITY");
    }

    private Path resolveUploadDirectory() {
        Path path = Paths.get(uploadRoot);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        return path.normalize();
    }

    private Path resolveStoredPath(String storedPath) {
        if (!hasText(storedPath)) {
            return null;
        }
        String normalized = storedPath.trim().replace('\\', '/');
        Path uploadDirectory = resolveUploadDirectory().normalize();
        String rootFolder = uploadDirectory.getFileName().toString();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String absoluteRoot = uploadDirectory.toString().replace('\\', '/');
        if (absoluteRoot.startsWith("/")) {
            absoluteRoot = absoluteRoot.substring(1);
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(normalized);
        if (normalized.startsWith(absoluteRoot + "/")) {
            candidates.add(normalized.substring(absoluteRoot.length() + 1));
        }
        if (normalized.startsWith(rootFolder + "/")) {
            candidates.add(normalized.substring(rootFolder.length() + 1));
        }
        String uploadPrefix = "uploads/pacs-results/";
        if (normalized.startsWith(uploadPrefix)) {
            candidates.add(normalized.substring(uploadPrefix.length()));
        }
        Path fallback = null;
        for (String candidate : candidates) {
            if (!hasText(candidate)) {
                continue;
            }
            Path path = uploadDirectory.resolve(candidate).normalize();
            if (!path.startsWith(uploadDirectory)) {
                continue;
            }
            if (Files.exists(path)) {
                return path;
            }
            if (fallback == null) {
                fallback = path;
            }
        }
        return fallback;
    }

    private String buildImageUrl(String imageKey) {
        try {
            var builder = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path(ApiConstants.PacsResult.BASE_PATH)
                    .path(ApiConstants.PacsResult.IMAGE_CONTENT_PATH);
            if (hasText(imageKey)) {
                builder.queryParam("imageKey", imageKey);
            }
            return builder.toUriString();
        } catch (Exception ex) {
            LOGGER.debug("ServletUriComponentsBuilder unavailable, using relative URL fallback: {}", ex.getMessage());
            return hasText(imageKey)
                    ? ApiConstants.PacsResult.BASE_PATH + ApiConstants.PacsResult.IMAGE_CONTENT_PATH + "?imageKey=" + imageKey
                    : ApiConstants.PacsResult.BASE_PATH + ApiConstants.PacsResult.IMAGE_CONTENT_PATH;
        }
    }

    private void deletePhysicalImageQuietly(String storedPath) {
        try {
            Path path = resolveStoredPath(storedPath);
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (Exception ex) {
            LOGGER.debug("Physical image deletion failed for '{}': {}", storedPath, ex.getMessage());
        }
    }

    private static String resultStatus(Boolean completed) {
        return Boolean.TRUE.equals(completed) ? RESULT_STATUS_COMPLETED : RESULT_STATUS_IMAGE_RECEIVED;
    }

    private static void trimFields(PacsResultSaveRequest request) {
        request.setWorklistCode(trimToNull(request.getWorklistCode()));
        request.setStudyInstanceUid(trimToNull(request.getStudyInstanceUid()));
        request.setAccessionNumber(trimToNull(request.getAccessionNumber()));
        request.setPatientCode(trimToNull(request.getPatientCode()));
        request.setPatientName(trimToNull(request.getPatientName()));
    }

    private static void trimContextRequest(PacsResultContextRequest request) {
        request.setStudyInstanceUid(trimToNull(request.getStudyInstanceUid()));
        request.setAccessionNumber(trimToNull(request.getAccessionNumber()));
        request.setPatientCode(trimToNull(request.getPatientCode()));
    }

    private static String normalizeRichText(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        trimmed = decodeBasicHtmlEntities(trimmed).trim();
        if (startsWithAllowedRichTextTag(trimmed)) {
            return sanitizeBasicRichText(trimmed);
        }
        return "<p>" + escapeBasicHtml(trimmed).replace("\n", "</p><p>") + "</p>";
    }

    private static boolean startsWithAllowedRichTextTag(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.matches("^<\\s*(p|div|ul|ol|li|h[1-6]|span|strong|b|em|i|u|br|table|thead|tbody|tfoot|tr|td|th|blockquote)\\b.*");
    }

    private static String decodeBasicHtmlEntities(String value) {
        String decoded = value;
        for (int i = 0; i < 5; i++) {
            String previous = decoded;
            decoded = decoded
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&#x27;", "'")
                    .replace("&amp;", "&");
            if (decoded.equals(previous)) {
                break;
            }
        }
        return decoded;
    }

    private static String sanitizeBasicRichText(String html) {
        return html
                .replaceAll("(?is)<\\s*(script|style)[^>]*>.*?<\\s*/\\s*\\1\\s*>", "")
                .replaceAll("(?i)\\s+on[a-z]+\\s*=\\s*\"[^\"]*\"", "")
                .replaceAll("(?i)\\s+on[a-z]+\\s*=\\s*'[^']*'", "")
                .replaceAll("(?i)\\s+on[a-z]+\\s*=\\s*[^\\s>]+", "")
                .replaceAll("(?i)href\\s*=\\s*\"\\s*javascript:[^\"]*\"", "href=\"#\"")
                .replaceAll("(?i)href\\s*=\\s*'\\s*javascript:[^']*'", "href=\"#\"");
    }

    private static boolean hasMeaningfulResultText(String html) {
        if (!hasText(html)) {
            return false;
        }
        String text = html.replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .trim();
        return !text.isEmpty();
    }

    private static String escapeBasicHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String sanitizeOriginalFilename(String originalFilename) {
        String name = originalFilename == null ? "" : Paths.get(originalFilename).getFileName().toString();
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (!hasText(name)) {
            throw new IllegalArgumentException("Image file name is required.");
        }
        return name;
    }

    private static String buildStoredFileName(Long resultId, String originalFilename, String extension) {
        String baseName = FilenameUtils.getBaseName(originalFilename);
        String safeBaseName = firstNonBlank(baseName, "result-image")
                .replaceAll("[^A-Za-z0-9_-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (!hasText(safeBaseName)) {
            safeBaseName = "result-image";
        }
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String resultPrefix = resultId == null || resultId <= 0 ? "RESULT" : "R" + resultId;
        return resultPrefix + "_" + safeBaseName + "_" + uniqueSuffix + "." + extension;
    }

    private static String safePathSegment(String value) {
        return safePathSegment(value, "MODALITY");
    }

    private static String safePathSegment(String value, String fallback) {
        String safeFallback = firstNonBlank(fallback, "SEGMENT").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_-]", "_");
        String segment = firstNonBlank(value, safeFallback).toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_-]", "_");
        return segment.isBlank() ? safeFallback : segment;
    }

    private static DetectedImageType detectImageType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xFF
                && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF) {
            return new DetectedImageType("jpg", "image/jpeg");
        }
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return new DetectedImageType("png", "image/png");
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return new DetectedImageType("webp", "image/webp");
        }
        return null;
    }

    private record DetectedImageType(String extension, String mimeType) {
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    private static Long positiveOrNull(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private static String firstNonBlank(String... values) {
        return FunctionHelper.firstNonBlankOrNull(values);
    }

    private record ResultAccess(ViewerAccessClaims claims) {
    }

    private static final class ResultAccessException extends RuntimeException {
        private final int statusCode;

        private ResultAccessException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        static ResultAccessException unauthorized(String message) {
            return new ResultAccessException(401, message);
        }

        static ResultAccessException forbidden(String message) {
            return new ResultAccessException(403, message);
        }

        int statusCode() {
            return statusCode;
        }
    }

    private record StoredResultImage(String storedPath, String originalFileName, String fileType, Long fileSize) {
    }
}
