package com.ut.emrPacs.service.serviceImpl;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.cache.config.CacheConfig;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.ut.emrPacs.model.dto.response.systemSettings.hospital.HospitalResponse;
import com.ut.emrPacs.model.dto.response.systemSettings.hospital.HospitalResponseDetail;
import com.ut.emrPacs.helper.CommonHelper;
import com.ut.emrPacs.helper.FunctionHelper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.modality.ModalityMapper;
import com.ut.emrPacs.model.components.systemSettings.modality.HospitalModalityRelation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import com.ut.emrPacs.mapper.hospital.HospitalMapper;
import com.ut.emrPacs.mapper.user.UserPermissionMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.HospitalListFilter;
import com.ut.emrPacs.model.components.systemSettings.hospital.Hospital;
import com.ut.emrPacs.model.dto.request.systemSettings.hospital.HospitalRequestUpdate;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.HospitalService;
import com.ut.emrPacs.service.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

@Service
public class HospitalServiceImpl implements HospitalService {
    private static final Long SUPER_ADMIN_USER_ID = 1L;
    private static final int HOSPITAL_MODALITY_CHUNK_SIZE = 500;
    private static final int MAX_PATH_SEGMENT_LENGTH = 80;
    private static final long DEFAULT_MAX_LOGO_BYTES = 2L * 1024L * 1024L;
    private static final Set<String> ALLOWED_LOGO_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @Value("${hospital.image.root-path:${HOSPITAL_IMAGE_ROOT_PATH:/var/ut-image}}")
    private String hospitalImageRootPath;
    @Value("${hospital.logo.max-bytes:2097152}")
    private long hospitalLogoMaxBytes;

    @Autowired
    private MessageService messageService;

    @Autowired 
    private ActivityLogService activityLogService;

    @Autowired
    private HospitalMapper hospitalMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserPermissionMapper permissionMapper;

    @Autowired
    private CommonHelper commonHelper;

    @Autowired
    private ModalityMapper modalityMapper;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> listHospital(HospitalListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load hospital and return API response.
        LocalTime startDuration = LocalTime.now();
        try {

            Pagination pagination = PaginationHelper.buildAndApplyOffset(filter);

            List<HospitalResponse> hospitalList = hospitalMapper.listHospital(filter);

            if (!hospitalList.isEmpty()) {
                for (HospitalResponse hospitalResponse: hospitalList) {
                    hospitalResponse.setHospitalUserList(hospitalMapper.getHospitalUserList(hospitalResponse.getId()));
                }
            }

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert("/hospital/hospital-list",null,null,"Hospital","Company (view)","View",1,"Success",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", hospitalList, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/hospital/hospital-list", errorLine, error.toString(),"Hospital","Company (view)","View",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> getHospitalById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load hospital by id and return API response.
        LocalTime startDuration = LocalTime.now();
        try {

            List<HospitalResponseDetail> hospitalResponseDetails = hospitalMapper.getHospitalById(id);

            hospitalResponseDetails.forEach(hospital -> {
                Long hospitalId = hospital.getId();
                hospital.setUserNameResponseList(hospitalMapper.getUserName(hospitalId));
            });



            LocalTime endDuration = LocalTime.now();
            activityLogService.insert("/hospital/hospital-find/{id}",null,null,"Hospital","Company (view)","View",1,"Success",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", hospitalResponseDetails, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/hospital/hospital-find/{id}", errorLine, error.toString(),"Hospital","Company (view)","View",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    /** {@inheritDoc} */
    @Override
    @CacheEvict(
            cacheNames = {
                    CacheConfig.DROPDOWN_HOSPITALS_BY_USER,
                    CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_USERS,
                    CacheConfig.DROPDOWN_PATIENTS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_USER_GROUPS
            },
            allEntries = true
    )
    public ResponseMessage<BaseResult> updateHospital(HospitalRequestUpdate hospitalRequestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return updateHospital(hospitalRequestUpdate, null, httpServletRequest);
    }

    /** {@inheritDoc} */
    @Override
    @CacheEvict(
            cacheNames = {
                    CacheConfig.DROPDOWN_HOSPITALS_BY_USER,
                    CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_USERS,
                    CacheConfig.DROPDOWN_PATIENTS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_USER_GROUPS
            },
            allEntries = true
    )
    public ResponseMessage<BaseResult> updateHospital(HospitalRequestUpdate hospitalRequestUpdate, MultipartFile logo, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Update flow: validate request, apply changes to hospital, and return operation result.
        LocalTime startDuration = LocalTime.now();
        try {
            if (hospitalRequestUpdate == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            hospitalRequestUpdate.setId(publicEntityKeyResolver.resolve(Entity.HOSPITAL, hospitalRequestUpdate.getPublicKey(), hospitalRequestUpdate.getId()));
            if (hospitalRequestUpdate.getId() == null || hospitalRequestUpdate.getId() <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Hospital id is required.", false));
            }
            List<HospitalResponseDetail> existingHospitals = hospitalMapper.getHospitalById(hospitalRequestUpdate.getId());
            HospitalResponseDetail existingHospital = existingHospitals == null || existingHospitals.isEmpty() ? null : existingHospitals.get(0);
            if (existingHospital == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Hospital not found.", false));
            }
            if (hospitalRequestUpdate.getCode() == null || hospitalRequestUpdate.getCode().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Hospital code is required.", false));
            }
            if (hospitalRequestUpdate.getName() == null || hospitalRequestUpdate.getName().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Hospital name is required.", false));
            }
            if (hospitalRequestUpdate.getAbbr() == null || hospitalRequestUpdate.getAbbr().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Hospital abbr is required.", false));
            }
            String trimmedCode = hospitalRequestUpdate.getCode().trim();
            String trimmedAbbr = normalizeHospitalAbbrForVisitCode(hospitalRequestUpdate.getAbbr());
            if (!FunctionHelper.isValidHospitalToken(trimmedAbbr)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Hospital abbr must contain 2-20 letters or numbers for Visit Code.", false));
            }
            String trimmedName = hospitalRequestUpdate.getName().trim();
            String identityLockError = validateHospitalIdentityLock(existingHospital, trimmedCode, trimmedAbbr, trimmedName, hospitalRequestUpdate.getTimezone());
            if (identityLockError != null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(identityLockError, false));
            }
            if (hospitalMapper.countHospitalCode(trimmedCode, hospitalRequestUpdate.getId()) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Hospital Code", false));
            }
            if (hospitalMapper.countHospitalAbbr(trimmedAbbr, hospitalRequestUpdate.getId()) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Hospital Abbr", false));
            }
            if (hospitalMapper.countHospitalVisitToken(trimmedAbbr, hospitalRequestUpdate.getId()) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Hospital Abbr for Visit Code", false));
            }

            applyPublicKeyRelations(hospitalRequestUpdate);
            List<Long> normalizedModalityIds = normalizeModalityIds(hospitalRequestUpdate.getModalityIds());
            String modalityValidationError = validateActiveModalityIds(normalizedModalityIds);
            if (modalityValidationError != null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(modalityValidationError, false));
            }

            String folderError = ensureHospitalImageFolders(trimmedCode, trimmedAbbr, trimmedName, normalizedModalityIds);
            if (folderError != null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(folderError, false));
            }

            Long userId = userService.getUserAuth().getId();

            Hospital hospital = new Hospital();
            hospital.setId(hospitalRequestUpdate.getId());
            hospital.setCode(trimmedCode);
            hospital.setAbbr(trimmedAbbr);
            hospital.setName(trimmedName);
            hospital.setNameOther(hospitalRequestUpdate.getNameKhmer());
            hospital.setTimezone(hospitalRequestUpdate.getTimezone());
            hospital.setModifiedBy(userId);

            String previousLogoPath = findHospitalLogoPath(hospital.getId());
            HospitalLogoFile logoFile = storeHospitalLogoIfPresent(hospitalRequestUpdate, logo);

            // Update hospital
            Boolean result = hospitalMapper.updateHospital(hospital);
            if (Boolean.TRUE.equals(result)) {
                applyHospitalLogoUpdateIfNeeded(hospital, logoFile, hospitalRequestUpdate.getRemoveLogo());
                deletePreviousLogoIfReplaced(previousLogoPath, logoFile, hospitalRequestUpdate.getRemoveLogo());
                if (logoFile == null && !Boolean.TRUE.equals(hospitalRequestUpdate.getRemoveLogo())) {
                    normalizeExistingHospitalLogoPath(hospital, previousLogoPath, trimmedCode, trimmedAbbr, trimmedName);
                }
            } else if (logoFile != null) {
                deleteLogoQuietly(logoFile.storedPath());
            }

            if (hospitalRequestUpdate.getHospitalUserList() != null) {
                insertHospitalUser(hospitalRequestUpdate.getHospitalUserList(), hospital.getId());
            } else {
                ensureSuperAdminHospitalUser(hospital.getId());
            }

            if (hospitalRequestUpdate.getModalityIds() != null) {
                if (hospital.getId() == null || hospital.getId() <= 0) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Hospital id is required to assign modalities.", false));
                }
                String relationError = replaceHospitalModality(hospital.getId(), normalizedModalityIds, userId);
                if (relationError != null) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message(relationError, false));
                }
            }

            if (Boolean.TRUE.equals(result)) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert("/hospital/hospital-update",null,null,"Hospital","Company (edit)","Edit",1,"Success",startDuration,endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            } else {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
            }

        } catch (IllegalArgumentException validationError) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (validationError.getStackTrace() != null && validationError.getStackTrace().length > 0) ? (long) validationError.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/hospital/hospital-update", errorLine, validationError.toString(),"Hospital","Company (edit)","Update",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/hospital/hospital-update", errorLine, error.toString(),"Hospital","Company (edit)","Update",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    /** {@inheritDoc} */
    @Override
    @CacheEvict(
            cacheNames = {
                    CacheConfig.DROPDOWN_HOSPITALS_BY_USER,
                    CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_USERS,
                    CacheConfig.DROPDOWN_PATIENTS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_USER_GROUPS
            },
            allEntries = true
    )
    public ResponseMessage<BaseResult> createHospital(HospitalRequestUpdate hospitalRequestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return createHospital(hospitalRequestUpdate, null, httpServletRequest);
    }

    /** {@inheritDoc} */
    @Override
    @CacheEvict(
            cacheNames = {
                    CacheConfig.DROPDOWN_HOSPITALS_BY_USER,
                    CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_USERS,
                    CacheConfig.DROPDOWN_PATIENTS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_USER_GROUPS
            },
            allEntries = true
    )
    public ResponseMessage<BaseResult> createHospital(HospitalRequestUpdate hospitalRequestUpdate, MultipartFile logo, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (hospitalRequestUpdate == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            if (hospitalRequestUpdate.getCode() == null || hospitalRequestUpdate.getCode().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Hospital code is required.", false));
            }
            if (hospitalRequestUpdate.getName() == null || hospitalRequestUpdate.getName().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Hospital name is required.", false));
            }
            if (hospitalRequestUpdate.getAbbr() == null || hospitalRequestUpdate.getAbbr().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Hospital abbr is required.", false));
            }

            String trimmedCode = hospitalRequestUpdate.getCode().trim();
            String trimmedAbbr = normalizeHospitalAbbrForVisitCode(hospitalRequestUpdate.getAbbr());
            if (!FunctionHelper.isValidHospitalToken(trimmedAbbr)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Hospital abbr must contain 2-20 letters or numbers for Visit Code.", false));
            }
            if (hospitalMapper.countHospitalCode(trimmedCode, null) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Hospital Code", false));
            }
            if (hospitalMapper.countHospitalAbbr(trimmedAbbr, null) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Hospital Abbr", false));
            }
            if (hospitalMapper.countHospitalVisitToken(trimmedAbbr, null) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Hospital Abbr for Visit Code", false));
            }
            String trimmedName = hospitalRequestUpdate.getName().trim();

            applyPublicKeyRelations(hospitalRequestUpdate);
            List<Long> normalizedModalityIds = normalizeModalityIds(hospitalRequestUpdate.getModalityIds());
            String modalityValidationError = validateActiveModalityIds(normalizedModalityIds);
            if (modalityValidationError != null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(modalityValidationError, false));
            }

            String folderError = ensureHospitalImageFolders(trimmedCode, trimmedAbbr, trimmedName, normalizedModalityIds);
            if (folderError != null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(folderError, false));
            }

            Long userId = userService.getUserAuth().getId();
            Hospital hospital = new Hospital();
            hospital.setCode(trimmedCode);
            hospital.setAbbr(trimmedAbbr);
            hospital.setName(trimmedName);
            hospital.setNameOther(hospitalRequestUpdate.getNameKhmer());
            hospital.setTimezone(hospitalRequestUpdate.getTimezone());
            hospital.setCreatedBy(userId);
            hospital.setModifiedBy(userId);

            HospitalLogoFile logoFile = storeHospitalLogoIfPresent(hospitalRequestUpdate, logo);
            Boolean result = hospitalMapper.createHospital(hospital);
            if (!Boolean.TRUE.equals(result) || hospital.getId() == null || hospital.getId() <= 0) {
                if (logoFile != null) {
                    deleteLogoQuietly(logoFile.storedPath());
                }
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
            }

            applyHospitalLogoUpdateIfNeeded(hospital, logoFile, hospitalRequestUpdate.getRemoveLogo());

            insertHospitalUser(hospitalRequestUpdate.getHospitalUserList(), hospital.getId());

            if (hospitalRequestUpdate.getModalityIds() != null) {
                String relationError = replaceHospitalModality(hospital.getId(), normalizedModalityIds, userId);
                if (relationError != null) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message(relationError, false));
                }
            }

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert("/hospital/hospital-create", null, null, "Hospital", "Company (add)", "Add", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
        } catch (IllegalArgumentException validationError) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (validationError.getStackTrace() != null && validationError.getStackTrace().length > 0) ? (long) validationError.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/hospital/hospital-create", errorLine, validationError.toString(), "Hospital", "Company (add)", "Add", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/hospital/hospital-create", errorLine, error.toString(), "Hospital", "Company (add)", "Add", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private HospitalLogoFile storeHospitalLogoIfPresent(HospitalRequestUpdate request, MultipartFile logo) throws IOException {
        if (logo == null || logo.isEmpty()) {
            return null;
        }
        if (logo.getSize() > maxHospitalLogoBytes()) {
            throw new IllegalArgumentException("Hospital logo file is too large.");
        }

        byte[] bytes = logo.getBytes();
        LogoImageType imageType = detectLogoImageType(bytes);
        if (imageType == null || !ALLOWED_LOGO_MIME_TYPES.contains(imageType.mimeType())) {
            throw new IllegalArgumentException("Hospital logo must be a valid JPG, PNG, or WebP image.");
        }

        String originalFileName = sanitizeOriginalFileName(logo.getOriginalFilename(), imageType.extension());
        String hospitalSegment = buildHospitalFolderName(request.getCode(), request.getAbbr(), request.getName());
        String storedFileName = "logo-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "." + imageType.extension();

        Path rootPath = resolveHospitalImageRootPath();
        Path directory = rootPath.resolve(hospitalSegment).resolve("LOGO").normalize();
        Files.createDirectories(directory);
        Path target = directory.resolve(storedFileName).normalize();
        if (!target.startsWith(rootPath)) {
            throw new IllegalArgumentException("Invalid hospital logo path.");
        }
        Files.write(target, bytes);

        return new HospitalLogoFile(
                "/" + hospitalSegment + "/LOGO/" + storedFileName,
                originalFileName,
                imageType.mimeType(),
                logo.getSize()
        );
    }

    private void applyHospitalLogoUpdateIfNeeded(Hospital hospital, HospitalLogoFile logoFile, Boolean removeLogo) {
        if (hospital == null || hospital.getId() == null || hospital.getId() <= 0) {
            return;
        }
        if (logoFile == null && !Boolean.TRUE.equals(removeLogo)) {
            return;
        }

        hospital.setLogoPath(logoFile == null ? null : logoFile.storedPath());
        hospital.setLogoFileName(logoFile == null ? null : logoFile.originalFileName());
        hospital.setLogoFileType(logoFile == null ? null : logoFile.mimeType());
        hospital.setLogoFileSize(logoFile == null ? null : logoFile.fileSize());
        hospitalMapper.updateHospitalLogo(hospital);
    }

    private String findHospitalLogoPath(Long hospitalId) {
        if (hospitalId == null || hospitalId <= 0) {
            return null;
        }
        List<HospitalResponseDetail> hospitals = hospitalMapper.getHospitalById(hospitalId);
        if (hospitals == null || hospitals.isEmpty()) {
            return null;
        }
        return hospitals.get(0).getLogoPath();
    }

    private void deletePreviousLogoIfReplaced(String previousLogoPath, HospitalLogoFile logoFile, Boolean removeLogo) {
        if (!Boolean.TRUE.equals(removeLogo) && logoFile == null) {
            return;
        }
        if (logoFile != null && Objects.equals(previousLogoPath, logoFile.storedPath())) {
            return;
        }
        deleteLogoQuietly(previousLogoPath);
    }

    private void normalizeExistingHospitalLogoPath(
            Hospital hospital,
            String previousLogoPath,
            String hospitalCode,
            String hospitalAbbr,
            String hospitalName
    ) {
        if (hospital == null || hospital.getId() == null || hospital.getId() <= 0) {
            return;
        }
        if (previousLogoPath == null || previousLogoPath.trim().isEmpty()) {
            return;
        }

        String hospitalSegment = buildHospitalFolderName(hospitalCode, hospitalAbbr, hospitalName);
        String normalizedStoredPath = previousLogoPath.trim().replace('\\', '/');
        if (normalizedStoredPath.startsWith("/")) {
            normalizedStoredPath = normalizedStoredPath.substring(1);
        }
        if (normalizedStoredPath.startsWith(hospitalSegment + "/LOGO/")) {
            return;
        }

        Path rootPath = resolveHospitalImageRootPath();
        Path source = resolveHospitalStoredPath(previousLogoPath);
        if (source == null || !Files.exists(source) || !Files.isRegularFile(source)) {
            return;
        }

        Path targetDirectory = rootPath.resolve(hospitalSegment).resolve("LOGO").normalize();
        Path target = targetDirectory.resolve(source.getFileName()).normalize();
        if (!target.startsWith(targetDirectory) || !target.startsWith(rootPath)) {
            return;
        }

        try {
            Files.createDirectories(targetDirectory);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

            List<HospitalResponseDetail> rows = hospitalMapper.getHospitalById(hospital.getId());
            if (rows == null || rows.isEmpty()) {
                return;
            }

            HospitalResponseDetail detail = rows.get(0);
            Hospital logoUpdate = new Hospital();
            logoUpdate.setId(hospital.getId());
            logoUpdate.setModifiedBy(hospital.getModifiedBy());
            logoUpdate.setLogoPath("/" + hospitalSegment + "/LOGO/" + target.getFileName());
            logoUpdate.setLogoFileName(detail.getLogoFileName());
            logoUpdate.setLogoFileType(detail.getLogoFileType());
            logoUpdate.setLogoFileSize(detail.getLogoFileSize());
            hospitalMapper.updateHospitalLogo(logoUpdate);

            Files.deleteIfExists(source);
            deleteEmptyDirectoriesUntilRoot(source.getParent(), rootPath);
        } catch (Exception ignored) {
            // Keep the existing DB logo path if filesystem normalization is not possible.
        }
    }

    private void deleteLogoQuietly(String storedPath) {
        try {
            Path path = resolveHospitalStoredPath(storedPath);
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
            // Logo cleanup should not fail the hospital save flow.
        }
    }

    private Path resolveHospitalImageRootPath() {
        Path rootPath = Paths.get(hospitalImageRootPath);
        if (!rootPath.isAbsolute()) {
            rootPath = Paths.get(System.getProperty("user.dir")).resolve(rootPath);
        }
        return rootPath.toAbsolutePath().normalize();
    }

    private Path resolveHospitalStoredPath(String storedPath) {
        if (storedPath == null || storedPath.trim().isEmpty()) {
            return null;
        }
        String normalized = storedPath.trim().replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path rootPath = resolveHospitalImageRootPath();
        Path path = rootPath.resolve(normalized).normalize();
        return path.startsWith(rootPath) ? path : null;
    }

    private void deleteEmptyDirectoriesUntilRoot(Path directory, Path rootPath) {
        Path current = directory;
        while (current != null && rootPath != null && current.startsWith(rootPath) && !current.equals(rootPath)) {
            try {
                Files.deleteIfExists(current);
            } catch (Exception ignored) {
                return;
            }
            current = current.getParent();
        }
    }

    private long maxHospitalLogoBytes() {
        return hospitalLogoMaxBytes > 0 ? hospitalLogoMaxBytes : DEFAULT_MAX_LOGO_BYTES;
    }

    private static LogoImageType detectLogoImageType(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return new LogoImageType("jpg", "image/jpeg");
        }
        if ((bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return new LogoImageType("png", "image/png");
        }
        String header = new String(bytes, 0, Math.min(bytes.length, 12), java.nio.charset.StandardCharsets.US_ASCII);
        if (header.startsWith("RIFF") && header.length() >= 12 && header.substring(8, 12).equals("WEBP")) {
            return new LogoImageType("webp", "image/webp");
        }
        return null;
    }

    private static String sanitizeOriginalFileName(String originalFilename, String extension) {
        String fileName = originalFilename == null ? "" : Paths.get(originalFilename).getFileName().toString();
        fileName = fileName.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (fileName.isEmpty()) {
            return "hospital-logo." + extension;
        }
        return fileName;
    }

    private void insertHospitalUser(List<Long> hospitalUserIds, Long companyId) {
        // Insert flow: validate request, create hospital user, and return operation result.
        commonHelper.replaceRelations(
                () -> hospitalMapper.deleteHospitalUser(companyId),
                withSuperAdminUserId(hospitalUserIds),
                userId -> userId != null && userId > 0,
                userId -> hospitalMapper.insertHospitalUser(userId, companyId)
        );
    }

    private void ensureSuperAdminHospitalUser(Long hospitalId) {
        if (hospitalId == null || hospitalId <= 0) {
            return;
        }
        hospitalMapper.insertHospitalUser(SUPER_ADMIN_USER_ID, hospitalId);
    }

    private static String normalizeHospitalAbbrForVisitCode(String abbr) {
        return FunctionHelper.normalizeHospitalToken(abbr);
    }

    private String validateHospitalIdentityLock(
            HospitalResponseDetail existingHospital,
            String requestedCode,
            String requestedAbbr,
            String requestedName,
            String requestedTimezone
    ) {
        if (existingHospital == null || !Boolean.TRUE.equals(existingHospital.getDeploymentLocked())) {
            return null;
        }

        String existingCode = normalizeComparableText(existingHospital.getCode());
        String existingAbbr = normalizeHospitalAbbrForVisitCode(existingHospital.getAbbr());
        String existingName = normalizeComparableText(existingHospital.getHospitalName());
        String existingTimezone = normalizeComparableText(existingHospital.getTimezone());
        String effectiveRequestedTimezone = normalizeComparableText(requestedTimezone);
        if (effectiveRequestedTimezone.isEmpty()) {
            effectiveRequestedTimezone = existingTimezone;
        }

        if (!existingCode.equals(normalizeComparableText(requestedCode))
                || !existingAbbr.equals(normalizeHospitalAbbrForVisitCode(requestedAbbr))
                || !existingName.equals(normalizeComparableText(requestedName))
                || !existingTimezone.equals(effectiveRequestedTimezone)) {
            return "Hospital code, abbreviation, name, and timezone are locked because a DICOM Routing deployment zip has already been downloaded.";
        }
        return null;
    }

    private static String normalizeComparableText(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<Long> withSuperAdminUserId(List<Long> userIds) {
        Set<Long> normalized = new LinkedHashSet<>();
        normalized.add(SUPER_ADMIN_USER_ID);
        if (userIds != null) {
            userIds.stream()
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .forEach(normalized::add);
        }
        return new ArrayList<>(normalized);
    }

    private String replaceHospitalModality(Long hospitalId, List<Long> modalityIds, Long userId) {
        List<Long> normalizedModalityIds = normalizeModalityIds(modalityIds);
        String validationError = validateActiveModalityIds(normalizedModalityIds);
        if (validationError != null) {
            return validationError;
        }

        modalityMapper.deactivateHospitalModalityByHospitalId(hospitalId, userId);
        if (normalizedModalityIds.isEmpty()) {
            return null;
        }

        for (int start = 0; start < normalizedModalityIds.size(); start += HOSPITAL_MODALITY_CHUNK_SIZE) {
            int end = Math.min(start + HOSPITAL_MODALITY_CHUNK_SIZE, normalizedModalityIds.size());
            List<HospitalModalityRelation> chunk = new ArrayList<>();
            for (Long modalityId : normalizedModalityIds.subList(start, end)) {
                HospitalModalityRelation relation = new HospitalModalityRelation();
                relation.setHospitalId(hospitalId);
                relation.setModalityId(modalityId);
                relation.setCreatedBy(userId);
                relation.setModifiedBy(userId);
                chunk.add(relation);
            }
            if (!chunk.isEmpty()) {
                modalityMapper.insertHospitalModalitiesBatch(chunk);
            }
        }
        return null;
    }

    private List<Long> normalizeModalityIds(List<Long> modalityIds) {
        if (modalityIds == null || modalityIds.isEmpty()) {
            return List.of();
        }
        return modalityIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    }

    private void applyPublicKeyRelations(HospitalRequestUpdate request) {
        if (request == null) {
            return;
        }
        List<Long> userIds = resolveKeyList(Entity.USER, request.getHospitalUserKeys(), "Hospital user");
        if (userIds != null) {
            request.setHospitalUserList(userIds);
        }
        List<Long> modalityIds = resolveKeyList(Entity.MODALITY, request.getModalityKeys(), "Modality");
        if (modalityIds != null) {
            request.setModalityIds(modalityIds);
        }
    }

    private List<Long> resolveKeyList(Entity entity, List<String> keys, String label) {
        if (keys == null) {
            return null;
        }
        return keys.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .map(key -> publicEntityKeyResolver.resolveRequired(entity, key, null, label))
                .distinct()
                .toList();
    }

    private String validateActiveModalityIds(List<Long> normalizedModalityIds) {
        if (normalizedModalityIds == null || normalizedModalityIds.isEmpty()) {
            return null;
        }
        Long totalActive = modalityMapper.countActiveModalitiesByIds(normalizedModalityIds);
        if (totalActive == null || totalActive != normalizedModalityIds.size()) {
            return "One or more modality ids are invalid or inactive.";
        }
        return null;
    }

    private String ensureHospitalImageFolders(String hospitalCode, String hospitalAbbr, String hospitalName, List<Long> modalityIds) {
        List<String> modalityNames = modalityIds == null || modalityIds.isEmpty()
                ? List.of()
                : modalityMapper.findActiveModalityNamesByIds(modalityIds);
        if (modalityIds != null && !modalityIds.isEmpty() && (modalityNames == null || modalityNames.isEmpty())) {
            return "No active modality names found for folder creation.";
        }

        String hospitalFolderName = buildHospitalFolderName(hospitalCode, hospitalAbbr, hospitalName);
        Path rootPath = resolveHospitalImageRootPath();
        Path hospitalFolder = rootPath.resolve(hospitalFolderName).normalize();
        if (!hospitalFolder.startsWith(rootPath)) {
            return "Invalid hospital image folder path.";
        }

        try {
            Files.createDirectories(hospitalFolder);
            Path logoFolder = hospitalFolder.resolve("LOGO").normalize();
            if (!logoFolder.startsWith(hospitalFolder)) {
                return "Invalid hospital logo folder path.";
            }
            Files.createDirectories(logoFolder);

            Set<String> uniqueModalityFolders = new LinkedHashSet<>();
            for (String modalityName : modalityNames) {
                String safeModality = sanitizePathSegment(modalityName);
                if (!safeModality.isEmpty()) {
                    uniqueModalityFolders.add(safeModality);
                }
            }

            for (String modalityFolderName : uniqueModalityFolders) {
                Path modalityFolder = hospitalFolder.resolve(modalityFolderName).normalize();
                if (!modalityFolder.startsWith(hospitalFolder)) {
                    return "Invalid modality image folder path.";
                }
                Files.createDirectories(modalityFolder);
            }
            return null;
        } catch (Exception exception) {
            return "Unable to prepare hospital modality image folders. Please check write permission for the PACS image folder.";
        }
    }

    private String buildHospitalFolderName(String hospitalCode, String hospitalAbbr, String hospitalName) {
        String safeCode = sanitizePathSegment(hospitalCode);
        String safeAbbr = sanitizePathSegment(hospitalAbbr);
        String safeName = sanitizePathSegment(hospitalName);
        if (!safeAbbr.isEmpty() && !safeName.isEmpty()) {
            return safeAbbr + "_" + safeName;
        }
        if (!safeCode.isEmpty() && !safeName.isEmpty()) {
            return safeCode + "_" + safeName;
        }
        if (!safeAbbr.isEmpty()) {
            return safeAbbr;
        }
        if (!safeCode.isEmpty()) {
            return safeCode;
        }
        if (!safeName.isEmpty()) {
            return safeName;
        }
        return "HOSPITAL";
    }

    private static String sanitizePathSegment(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        String sanitized = trimmed.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_");
        sanitized = sanitized.replaceAll("\\s+", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^[._]+", "");
        sanitized = sanitized.replaceAll("[._]+$", "");
        if (sanitized.equals(".") || sanitized.equals("..")) {
            return "";
        }
        if (sanitized.length() > MAX_PATH_SEGMENT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_PATH_SEGMENT_LENGTH);
        }
        return sanitized.toUpperCase(Locale.ROOT);
    }

    private record HospitalLogoFile(String storedPath, String originalFileName, String mimeType, Long fileSize) {
    }

    private record LogoImageType(String extension, String mimeType) {
    }

}

