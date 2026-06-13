package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.cache.config.CacheConfig;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.modality.ModalityMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.ModalityFilter;
import com.ut.emrPacs.model.components.systemSettings.modality.Modality;
import com.ut.emrPacs.model.dto.request.systemSettings.hospital.HospitalModalityRequest;
import com.ut.emrPacs.model.dto.request.systemSettings.modality.ModalityRequestUpdate;
import com.ut.emrPacs.model.dto.response.systemSettings.modality.HospitalModalityFlatRow;
import com.ut.emrPacs.model.dto.response.systemSettings.modality.HospitalModalityItemResponse;
import com.ut.emrPacs.model.dto.response.systemSettings.modality.HospitalModalityResponse;
import com.ut.emrPacs.model.dto.response.systemSettings.modality.HospitalWithModalitiesResponse;
import com.ut.emrPacs.model.dto.response.systemSettings.modality.ModalityResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.ModalityService;
import com.ut.emrPacs.service.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.net.UnknownHostException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModalityServiceImpl implements ModalityService {

    @Autowired
    private ModalityMapper modalityMapper;

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserService userService;

    @Autowired
    private ActivityLogService activityLogService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @Override
    public ResponseMessage<BaseResult> listModality(ModalityFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Pagination pagination = PaginationHelper.buildAndApplyOffset(filter, modalityMapper.countModality(filter));

            List<ModalityResponse> modalities = modalityMapper.listModality(filter);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Modality.BASE_PATH + ApiConstants.Modality.LIST_PATH, null, null, "Modality", "Modality (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", modalities, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Modality.BASE_PATH + ApiConstants.Modality.LIST_PATH, errorLine, error.toString(), "Modality", "Modality (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getModalityById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            List<ModalityResponse> modalities = modalityMapper.getModalityById(id);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Modality.BASE_PATH + ApiConstants.Modality.FIND_PATH, null, null, "Modality", "Modality (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", modalities, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Modality.BASE_PATH + ApiConstants.Modality.FIND_PATH, errorLine, error.toString(), "Modality", "Modality (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(
            cacheNames = {
                    CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_MODALITY_CATALOG,
                    CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL
            },
            allEntries = true
    )
    public ResponseMessage<BaseResult> createModality(ModalityRequestUpdate modalityRequestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (modalityRequestUpdate == null || modalityRequestUpdate.getName() == null || modalityRequestUpdate.getName().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Modality name is required.", false));
            }
            if (modalityRequestUpdate.getAbbr() == null || modalityRequestUpdate.getAbbr().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Modality abbr is required.", false));
            }

            String name = modalityRequestUpdate.getName().trim();
            String abbr = modalityRequestUpdate.getAbbr().trim().toUpperCase();
            if (modalityMapper.countActiveModalityByName(name, null) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Modality Name", false));
            }
            if (modalityMapper.countActiveModalityByAbbr(abbr, null) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Modality Abbr", false));
            }

            Long userId = userService.getUserAuth().getId();
            Modality modality = new Modality();
            modality.setAbbr(abbr);
            modality.setName(name);
            modality.setIsActive(modalityRequestUpdate.getIsActive() == null ? 1L : modalityRequestUpdate.getIsActive());
            modality.setCreatedBy(userId);
            modality.setModifiedBy(userId);

            Boolean result = modalityMapper.createModality(modality);
            if (Boolean.TRUE.equals(result)) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.Modality.BASE_PATH + ApiConstants.Modality.CREATE_PATH, null, null, "Modality", "Modality (Add)", "Add", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }

            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Modality.BASE_PATH + ApiConstants.Modality.CREATE_PATH, errorLine, error.toString(), "Modality", "Modality (Add)", "Add", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(
            cacheNames = {
                    CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_MODALITY_CATALOG,
                    CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL
            },
            allEntries = true
    )
    public ResponseMessage<BaseResult> updateModality(ModalityRequestUpdate modalityRequestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (modalityRequestUpdate == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            modalityRequestUpdate.setId(publicEntityKeyResolver.resolve(Entity.MODALITY, modalityRequestUpdate.getPublicKey(), modalityRequestUpdate.getId()));
            if (modalityRequestUpdate.getId() == null || modalityRequestUpdate.getId() <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Modality id is required.", false));
            }

            if (modalityRequestUpdate.getName() == null || modalityRequestUpdate.getName().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Modality name is required.", false));
            }
            if (modalityRequestUpdate.getAbbr() == null || modalityRequestUpdate.getAbbr().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Modality abbr is required.", false));
            }

            List<ModalityResponse> existing = modalityMapper.getModalityById(modalityRequestUpdate.getId());
            if (existing == null || existing.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Modality not found.", false));
            }

            String name = modalityRequestUpdate.getName().trim();
            String abbr = modalityRequestUpdate.getAbbr().trim().toUpperCase();
            if (modalityMapper.countActiveModalityByName(name, modalityRequestUpdate.getId()) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Modality Name", false));
            }
            if (modalityMapper.countActiveModalityByAbbr(abbr, modalityRequestUpdate.getId()) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Modality Abbr", false));
            }

            Long userId = userService.getUserAuth().getId();
            Modality modality = new Modality();
            modality.setId(modalityRequestUpdate.getId());
            modality.setAbbr(abbr);
            modality.setName(name);
            modality.setIsActive(modalityRequestUpdate.getIsActive());
            modality.setModifiedBy(userId);

            Boolean result = modalityMapper.updateModality(modality);
            if (Boolean.TRUE.equals(result)) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.Modality.BASE_PATH + ApiConstants.Modality.UPDATE_PATH, null, null, "Modality", "Modality (Edit)", "Edit", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }

            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Modality.BASE_PATH + ApiConstants.Modality.UPDATE_PATH, errorLine, error.toString(), "Modality", "Modality (Edit)", "Edit", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(
            cacheNames = {
                    CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_MODALITY_CATALOG,
                    CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL
            },
            allEntries = true
    )
    public ResponseMessage<BaseResult> deleteModality(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (id == null || id <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Modality id is required.", false));
            }
            List<String> routeUsage = modalityMapper.listActiveDicomRouteUsageByModalityId(id);
            if (routeUsage != null && !routeUsage.isEmpty()) {
                return ResponseMessageUtils.makeResponse(
                        false,
                        messageService.message(buildRouteRelationMessage(), routeUsage, false)
                );
            }
            List<String> machineUsage = modalityMapper.listActiveMachineUsageByModalityId(id);
            if (machineUsage != null && !machineUsage.isEmpty()) {
                return ResponseMessageUtils.makeResponse(
                        false,
                        messageService.message(buildMachineRelationMessage(), machineUsage, false)
                );
            }
            Long userId = userService.getUserAuth().getId();
            Boolean result = modalityMapper.deleteModality(id, userId);
            if (Boolean.TRUE.equals(result)) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.Modality.BASE_PATH + ApiConstants.Modality.DELETE_PATH, null, null, "Modality", "Modality (Delete)", "Delete", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("This modality is related to active Machines or DICOM Routing. Remove related records first.", false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Modality.BASE_PATH + ApiConstants.Modality.DELETE_PATH, errorLine, error.toString(), "Modality", "Modality (Delete)", "Delete", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private static String buildRouteRelationMessage() {
        return "This modality is related to active DICOM Routing. Remove the related DICOM Routing records first.";
    }

    private static String buildMachineRelationMessage() {
        return "This modality is related to active Machines. Remove or deactivate those machines first.";
    }

    @Override
    public ResponseMessage<BaseResult> listHospitalModalityByUser(HospitalModalityRequest hospitalModalityRequest, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long currentUserId = userService.getUserAuth().getId();
            Long targetUserId = currentUserId;
            if (hospitalModalityRequest != null && hospitalModalityRequest.getUserKey() != null && !hospitalModalityRequest.getUserKey().isBlank()) {
                Long requestedUserId = publicEntityKeyResolver.resolve(Entity.USER, hospitalModalityRequest.getUserKey(), null);
                if (requestedUserId == null || !requestedUserId.equals(currentUserId)) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Forbidden.", false));
                }
                targetUserId = requestedUserId;
            }

            List<HospitalModalityFlatRow> rows = modalityMapper.listHospitalModalityByUserId(targetUserId);
            Map<Long, HospitalWithModalitiesResponse> hospitalMap = new LinkedHashMap<>();

            for (HospitalModalityFlatRow row : rows) {
                if (row == null || row.getHospitalId() == null) {
                    continue;
                }
                HospitalWithModalitiesResponse hospital = hospitalMap.computeIfAbsent(row.getHospitalId(), id -> {
                    HospitalWithModalitiesResponse value = new HospitalWithModalitiesResponse();
                    value.setId(id);
                    value.setPublicKey(row.getHospitalPublicKey());
                    value.setName(row.getHospitalName());
                    return value;
                });

                if (row.getModalityId() != null) {
                    HospitalModalityItemResponse modality = new HospitalModalityItemResponse();
                    modality.setId(row.getModalityId());
                    modality.setPublicKey(row.getModalityPublicKey());
                    modality.setName(row.getModalityName());
                    hospital.getModalities().add(modality);
                }
            }

            HospitalModalityResponse response = new HospitalModalityResponse();
            response.setHospitals(new ArrayList<>(hospitalMap.values()));

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.HospitalModality.LIST_BY_USER_PATH, null, null, "Hospital", "Hospital Modality (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(response), true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.HospitalModality.LIST_BY_USER_PATH, errorLine, error.toString(), "Hospital", "Hospital Modality (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }
}
