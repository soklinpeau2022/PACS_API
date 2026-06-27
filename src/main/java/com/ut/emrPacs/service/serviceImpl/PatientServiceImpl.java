package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.cache.config.CacheConfig;
import com.ut.emrPacs.helper.FunctionCodeGenerate;
import com.ut.emrPacs.helper.FunctionHelper;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.hospital.HospitalMapper;
import com.ut.emrPacs.mapper.pacs.PatientMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.PatientListFilter;
import com.ut.emrPacs.model.dto.request.pacs.patient.PatientCreateRequest;
import com.ut.emrPacs.model.dto.request.pacs.patient.PatientUpdateRequest;
import com.ut.emrPacs.model.dto.response.systemSettings.hospital.HospitalResponseDetail;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.PatientService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import static com.ut.emrPacs.authentication.util.AuthorityUtils.isAdminUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class PatientServiceImpl implements PatientService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PatientServiceImpl.class);
    private static final LocalDate DEFAULT_DATE_OF_BIRTH = LocalDate.of(1900, 1, 1);
    private static final int PATIENT_CODE_CREATE_RETRY = 5;

    @Autowired
    private PatientMapper patientMapper;
    @Autowired
    private HospitalMapper hospitalMapper;
    @Autowired
    private MessageService messageService;
    @Autowired
    private ActivityLogService activityLogService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @Override
    public ResponseMessage<BaseResult> list(PatientListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            PatientListFilter safeFilter = filter != null ? filter : new PatientListFilter();
            Long requestedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, safeFilter.getHospitalKey(), null);
            Long hospitalId = resolveOptionalHospitalId(requestedHospitalId);
            Long total = patientMapper.count(hospitalId, safeFilter);
            Pagination pagination = PaginationHelper.buildAndApplyOffsetOrDefault(safeFilter, total);
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert("/patient/patient-list", null, null, "Patient", "Patient (List)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(
                    "Success",
                    patientMapper.list(hospitalId, safeFilter),
                    pagination,
                    true
            ));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/patient/patient-list", errorLine, error.toString(), "Patient", "Patient (List)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> findById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            var patient = patientMapper.findById(resolveOptionalHospitalId(null), id);
            if (patient == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Patient not found.", false));
            }
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert("/patient/patient-find/{id}", null, null, "Patient", "Patient (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(patient), true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/patient/patient-find/{id}", errorLine, error.toString(), "Patient", "Patient (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(cacheNames = { CacheConfig.DROPDOWN_PATIENTS_BY_HOSPITAL }, allEntries = true)
    public ResponseMessage<BaseResult> create(PatientCreateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            Long requestedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
            Long hospitalId = resolveHospitalId(requestedHospitalId);

            String firstName = FunctionHelper.trimToNull(request.getFirstName());
            String lastName = FunctionHelper.trimToNull(request.getLastName());
            if (firstName == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("firstName is required.", false));
            }

            request.setFirstName(firstName);
            request.setLastName(lastName == null ? "" : lastName);
            request.setPhoneNumber(FunctionHelper.trimToNull(request.getPhoneNumber()));
            request.setGender(FunctionHelper.trimToNull(request.getGender()));
            if (request.getDateOfBirth() == null) {
                request.setDateOfBirth(DEFAULT_DATE_OF_BIRTH);
            }

            for (int attempt = 0; attempt < PATIENT_CODE_CREATE_RETRY; attempt++) {
                request.setPatientCode(generatePatientCode(hospitalId));
                try {
                    Long patientId = patientMapper.create(hospitalId, request);
                    if (patientId != null && patientId > 0) {
                        var createdPatient = patientMapper.findById(hospitalId, patientId);
                        if (createdPatient == null) {
                            return ResponseMessageUtils.makeResponse(false, messageService.message("Patient was created but could not be loaded.", false));
                        }
                        LocalTime endDuration = LocalTime.now();
                        activityLogService.insert("/patient/patient-create", null, null, "Patient", "Patient (Create)", "Add", 1, "Success", startDuration, endDuration, httpServletRequest);
                        return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(createdPatient), true));
                    }
                } catch (DuplicateKeyException duplicateError) {
                    if (attempt == PATIENT_CODE_CREATE_RETRY - 1) {
                        throw duplicateError;
                    }
                }
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Create failed", false));
        } catch (DuplicateKeyException duplicateError) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (duplicateError.getStackTrace() != null && duplicateError.getStackTrace().length > 0) ? (long) duplicateError.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/patient/patient-create", errorLine, duplicateError.toString(), "Patient", "Patient (Create)", "Add", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("Patient code already exists.", false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/patient/patient-create", errorLine, error.toString(), "Patient", "Patient (Create)", "Add", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(cacheNames = { CacheConfig.DROPDOWN_PATIENTS_BY_HOSPITAL }, allEntries = true)
    public ResponseMessage<BaseResult> update(PatientUpdateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            request.setId(publicEntityKeyResolver.resolve(Entity.PATIENT, request.getPublicKey(), request.getId()));
            if (request.getId() == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Patient id is required.", false));
            }
            Long requestedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
            Long hospitalId = resolveHospitalId(requestedHospitalId);
            var existingPatient = patientMapper.findById(hospitalId, request.getId());
            if (existingPatient == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Patient not found.", false));
            }

            String patientCode = FunctionHelper.trimToNull(request.getPatientCode());
            String firstName = FunctionHelper.trimToNull(request.getFirstName());
            boolean firstNameProvided = request.getFirstName() != null;
            String lastName = FunctionHelper.trimToNull(request.getLastName());
            boolean lastNameProvided = request.getLastName() != null;
            if (firstNameProvided && firstName == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("firstName is required.", false));
            }
            if (patientCode != null) {
                Long duplicateCode = patientMapper.countDuplicatePatientCode(patientCode, request.getId());
                if (duplicateCode != null && duplicateCode > 0) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Patient code already exists.", false));
                }
            }

            request.setPatientCode(patientCode);
            request.setFirstName(firstNameProvided ? firstName : null);
            request.setLastName(lastNameProvided ? (lastName == null ? "" : lastName) : null);
            request.setPhoneNumber(FunctionHelper.trimToNull(request.getPhoneNumber()));
            request.setGender(FunctionHelper.trimToNull(request.getGender()));
            if (request.getDateOfBirth() == null && existingPatient.getDateOfBirth() == null) {
                request.setDateOfBirth(DEFAULT_DATE_OF_BIRTH);
            }

            Boolean ok = patientMapper.update(hospitalId, request);
            if (ok != null && ok) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert("/patient/patient-update", null, null, "Patient", "Patient (Update)", "Edit", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Update failed", false));
        } catch (DuplicateKeyException duplicateError) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (duplicateError.getStackTrace() != null && duplicateError.getStackTrace().length > 0) ? (long) duplicateError.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/patient/patient-update", errorLine, duplicateError.toString(), "Patient", "Patient (Update)", "Edit", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("Patient code already exists.", false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/patient/patient-update", errorLine, error.toString(), "Patient", "Patient (Update)", "Edit", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private static Long currentHospitalId() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null || principal.hospitalId() == null) {
            throw new IllegalStateException("Hospital context not found in OAuth2 token claims.");
        }
        return principal.hospitalId();
    }

    private static Long resolveHospitalId(Long requestedHospitalId) {
        if (requestedHospitalId != null && requestedHospitalId > 0 && isAdminUser()) {
            return requestedHospitalId;
        }
        return currentHospitalId();
    }

    private static Long resolveOptionalHospitalId(Long requestedHospitalId) {
        if (isAdminUser()) {
            return requestedHospitalId != null && requestedHospitalId > 0 ? requestedHospitalId : null;
        }
        return currentHospitalId();
    }

    private String generatePatientCode(Long hospitalId) {
        String yearPrefix = FunctionCodeGenerate.currentPatientYearPrefix();
        String hospitalToken = resolvePatientHospitalToken(hospitalId);
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

    private String resolvePatientHospitalToken(Long hospitalId) {
        if (hospitalId == null || hospitalId <= 0L) {
            return "HOSP";
        }
        try {
            List<HospitalResponseDetail> rows = hospitalMapper.getHospitalById(hospitalId);
            if (rows != null && !rows.isEmpty() && rows.get(0) != null) {
                HospitalResponseDetail hospital = rows.get(0);
                String token = FunctionHelper.normalizeHospitalToken(hospital.getAbbr());
                if (FunctionHelper.hasText(token)) {
                    return token;
                }
                token = FunctionHelper.normalizeHospitalToken(hospital.getCode());
                if (FunctionHelper.hasText(token)) {
                    return token;
                }
                token = FunctionHelper.normalizeHospitalToken(hospital.getHospitalName());
                if (FunctionHelper.hasText(token)) {
                    return token;
                }
            }
        } catch (Exception ex) {
            LOGGER.debug("Hospital metadata lookup failed for id {}, using numeric fallback: {}", hospitalId, ex.getMessage());
        }
        return "H" + hospitalId;
    }
}
