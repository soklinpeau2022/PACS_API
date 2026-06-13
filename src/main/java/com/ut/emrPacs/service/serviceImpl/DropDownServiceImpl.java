package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.cache.dropdown.DropDownCacheService;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.mapper.user.UserMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.DropDownFilter;
import com.ut.emrPacs.model.dto.response.dropDown.DicomServerDropDownResponse;
import com.ut.emrPacs.model.dto.response.dropDown.DropDownModelResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DropDownService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.UnknownHostException;
import java.time.LocalTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DropDownServiceImpl implements DropDownService {

    private static final String CROSS_HOSPITAL_USER_SCOPE_ROLE_CODE = "USER_HOSPITAL_SCOPE_ALL";

    @Autowired
    private DropDownCacheService dropDownCacheService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @Override
    public ResponseMessage<BaseResult> getListCountries(DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Pagination pagination = PaginationHelper.buildAndApplyOffset(filter);
            List<DropDownModelResponse> responses = dropDownCacheService.getListCountries(filter);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    "/dropdown/dropdown-nationality",
                    null,
                    null,
                    "Country",
                    "Country (View)",
                    "View",
                    1,
                    "Success",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", responses, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(
                    "/dropdown/dropdown-nationality",
                    errorLine,
                    error.toString(),
                    "Country",
                    "Country (View)",
                    "View",
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getListHospitalsByUser(DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            var principal = UserAuthSession.getCurrentUser();
            if (principal == null || principal.userId() == null) {
                return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
            }

            Pagination pagination = PaginationHelper.buildAndApplyOffset(filter);
            List<DropDownModelResponse> responses = dropDownCacheService.getListHospitalsByUserId(principal.userId(), filter);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    "/dropdown/dropdown-hospital",
                    null,
                    null,
                    "Hospital",
                    "Hospital (Dropdown By User)",
                    "View",
                    1,
                    "Success",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", responses, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(
                    "/dropdown/dropdown-hospital",
                    errorLine,
                    error.toString(),
                    "Hospital",
                    "Hospital (Dropdown By User)",
                    "View",
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getListModalities(DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long hospitalId = resolveDropdownHospitalId(filter);
            if (hospitalId == null) {
                return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
            }

            Pagination pagination = PaginationHelper.buildAndApplyOffset(filter);
            List<DropDownModelResponse> responses = dropDownCacheService.getListModalitiesByHospitalId(hospitalId, filter);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    "/dropdown/dropdown-modality",
                    null,
                    null,
                    "Modality",
                    "Modality (Dropdown)",
                    "View",
                    1,
                    "Success",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", responses, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(
                    "/dropdown/dropdown-modality",
                    errorLine,
                    error.toString(),
                    "Modality",
                    "Modality (Dropdown)",
                    "View",
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getListModalityCatalog(DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            var principal = UserAuthSession.getCurrentUser();
            if (principal == null || principal.userId() == null) {
                return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
            }

            Pagination pagination = PaginationHelper.buildAndApplyOffset(filter);
            List<DropDownModelResponse> responses = dropDownCacheService.getListModalityCatalog(filter);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    "/dropdown/dropdown-modality-catalog",
                    null,
                    null,
                    "Modality",
                    "Modality Catalog (Dropdown)",
                    "View",
                    1,
                    "Success",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", responses, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(
                    "/dropdown/dropdown-modality-catalog",
                    errorLine,
                    error.toString(),
                    "Modality",
                    "Modality Catalog (Dropdown)",
                    "View",
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getListDicomServers(DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long hospitalId = resolveDropdownHospitalId(filter);
            if (hospitalId == null) {
                return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
            }
            if (filter != null && filter.getIncludeDicomServerId() == null) {
                filter.setIncludeDicomServerId(publicEntityKeyResolver.resolve(Entity.DICOM_SERVER, filter.getIncludeDicomServerKey(), null));
            }

            Pagination pagination = PaginationHelper.buildAndApplyOffset(filter);
            List<DicomServerDropDownResponse> responses = dropDownCacheService.getListDicomServersByHospitalId(hospitalId, filter);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    "/dropdown/dropdown-dicom-server",
                    null,
                    null,
                    "DICOM Server",
                    "DICOM Server (Dropdown)",
                    "View",
                    1,
                    "Success",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", responses, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(
                    "/dropdown/dropdown-dicom-server",
                    errorLine,
                    error.toString(),
                    "DICOM Server",
                    "DICOM Server (Dropdown)",
                    "View",
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getListUserGroupMembers(DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            var principal = UserAuthSession.getCurrentUser();
            if (principal == null || principal.userId() == null || principal.hospitalId() == null) {
                return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
            }

            Long targetHospitalId = filter == null ? null : filter.getHospitalId();
            boolean canViewAllHospitals = canAccessAllUserHospitals(principal.userId());
            if (targetHospitalId == null || targetHospitalId <= 0 || !canViewAllHospitals) {
                targetHospitalId = principal.hospitalId();
            }

            Pagination pagination = PaginationHelper.buildAndApplyOffset(filter);
            List<DropDownModelResponse> responses = userMapper.getMemberDropdownByHospital(targetHospitalId, filter);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    "/dropdown/dropdown-user-group-member",
                    null,
                    null,
                    "User Group",
                    "User Group Member (Dropdown)",
                    "View",
                    1,
                    "Success",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", responses, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(
                    "/dropdown/dropdown-user-group-member",
                    errorLine,
                    error.toString(),
                    "User Group",
                    "User Group Member (Dropdown)",
                    "View",
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getListUsers(DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            var principal = UserAuthSession.getCurrentUser();
            if (principal == null || principal.userId() == null || principal.hospitalId() == null) {
                return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
            }

            Long targetHospitalId = filter == null ? null : filter.getHospitalId();
            boolean canViewAllHospitals = canAccessAllUserHospitals(principal.userId());
            if (targetHospitalId == null || targetHospitalId <= 0) {
                targetHospitalId = canViewAllHospitals ? null : principal.hospitalId();
            } else if (!canViewAllHospitals) {
                targetHospitalId = principal.hospitalId();
            }

            Pagination pagination = PaginationHelper.buildAndApplyOffset(filter);
            List<DropDownModelResponse> responses = dropDownCacheService.getListUsers(targetHospitalId, filter);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    "/dropdown/dropdown-user",
                    null,
                    null,
                    "User",
                    "User (Dropdown)",
                    "View",
                    1,
                    "Success",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", responses, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(
                    "/dropdown/dropdown-user",
                    errorLine,
                    error.toString(),
                    "User",
                    "User (Dropdown)",
                    "View",
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getListPatients(DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            var principal = UserAuthSession.getCurrentUser();
            if (principal == null || principal.userId() == null || principal.hospitalId() == null) {
                return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
            }

            Long targetHospitalId = filter == null ? null : filter.getHospitalId();
            boolean canViewAllHospitals = canAccessAllUserHospitals(principal.userId());
            if (targetHospitalId == null || targetHospitalId <= 0) {
                targetHospitalId = principal.hospitalId();
            } else if (!canViewAllHospitals) {
                targetHospitalId = principal.hospitalId();
            }

            Pagination pagination = PaginationHelper.buildAndApplyOffset(filter);
            List<DropDownModelResponse> responses = dropDownCacheService.getListPatientsByHospitalId(targetHospitalId, filter);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    "/dropdown/dropdown-patient",
                    null,
                    null,
                    "Patient",
                    "Patient (Dropdown)",
                    "View",
                    1,
                    "Success",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", responses, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(
                    "/dropdown/dropdown-patient",
                    errorLine,
                    error.toString(),
                    "Patient",
                    "Patient (Dropdown)",
                    "View",
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getListUserGroups(DropDownFilter filter, HttpServletRequest httpServletRequest)
            throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            var principal = UserAuthSession.getCurrentUser();
            if (principal == null || principal.userId() == null || principal.hospitalId() == null) {
                return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
            }

            boolean canViewAllHospitals = canAccessAllUserHospitals(principal.userId());
            boolean isSuperAdmin = principal.userId() == 1L;
            Long targetHospitalId = filter == null ? null : filter.getHospitalId();
            if (targetHospitalId != null && targetHospitalId <= 0) {
                targetHospitalId = null;
            }
            if (!canViewAllHospitals) {
                targetHospitalId = principal.hospitalId();
            }

            Pagination pagination = PaginationHelper.buildAndApplyOffset(filter);
            List<DropDownModelResponse> responses = dropDownCacheService.getListUserGroups(
                    targetHospitalId,
                    principal.hospitalId(),
                    canViewAllHospitals,
                    isSuperAdmin,
                    filter
            );

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    "/dropdown/dropdown-user-group",
                    null,
                    null,
                    "User Group",
                    "User Group (Dropdown)",
                    "View",
                    1,
                    "Success",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", responses, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(
                    "/dropdown/dropdown-user-group",
                    errorLine,
                    error.toString(),
                    "User Group",
                    "User Group (Dropdown)",
                    "View",
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private boolean canAccessAllUserHospitals(Long userId) {
        if (userId == null) {
            return false;
        }
        if (userId == 1L) {
            return true;
        }
        Long count = userMapper.countCrossHospitalScopeRoleByUserId(userId, CROSS_HOSPITAL_USER_SCOPE_ROLE_CODE);
        return count != null && count > 0;
    }

    private Long resolveDropdownHospitalId(DropDownFilter filter) {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null || principal.userId() == null || principal.hospitalId() == null) {
            return null;
        }

        Long requestedHospitalId = filter == null ? null : publicEntityKeyResolver.resolve(Entity.HOSPITAL, filter.getHospitalKey(), filter.getHospitalId());
        if (requestedHospitalId == null || requestedHospitalId <= 0L) {
            return principal.hospitalId();
        }
        if (principal.userId() == 1L || canAccessAllUserHospitals(principal.userId())) {
            return requestedHospitalId;
        }

        Long assignedHospitalCount = userMapper.countActiveUserInHospital(principal.userId(), requestedHospitalId);
        return assignedHospitalCount != null && assignedHospitalCount > 0
                ? requestedHospitalId
                : principal.hospitalId();
    }
}

