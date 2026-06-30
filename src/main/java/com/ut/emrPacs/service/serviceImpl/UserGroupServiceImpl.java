package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.cache.permission.PermissionCacheService;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.permission.PermissionMapper;
import com.ut.emrPacs.mapper.permission.RolePermissionMapper;
import com.ut.emrPacs.mapper.user.ModuleMapper;
import com.ut.emrPacs.mapper.user.ModuleTypeMapper;
import com.ut.emrPacs.mapper.user.RoleMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.ModuleTypeFilter;
import com.ut.emrPacs.model.base.filter.RoleListFilter;
import com.ut.emrPacs.model.dto.request.authentication.userGroup.UserGroupCreateRequest;
import com.ut.emrPacs.model.dto.request.authentication.userGroup.UserGroupListRequest;
import com.ut.emrPacs.model.dto.request.authentication.userGroup.UserGroupModuleSelectionRequest;
import com.ut.emrPacs.model.dto.request.authentication.userGroup.UserGroupModuleTypeSelectionRequest;
import com.ut.emrPacs.model.dto.request.authentication.userGroup.UserGroupUpdateRequest;
import com.ut.emrPacs.model.dto.response.authentication.module.ModuleType;
import com.ut.emrPacs.model.dto.response.authentication.role.RoleResponse;
import com.ut.emrPacs.model.dto.response.authentication.user.UserGroupResponse;
import com.ut.emrPacs.model.dto.response.authentication.user.UserGroupUserResponse;
import com.ut.emrPacs.model.enums.UserGroupType;
import com.ut.emrPacs.model.role.Role;
import com.ut.emrPacs.model.role.RoleUser;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.UserGroupService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserGroupServiceImpl implements UserGroupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserGroupServiceImpl.class);
    private static final Long SUPER_ADMIN_USER_ID = 1L;
    private static final int MAX_ROLE_FETCH_SIZE = 10_000;
    private static final String CROSS_HOSPITAL_USER_SCOPE_ROLE_CODE = "USER_HOSPITAL_SCOPE_ALL";
    private static final DateTimeFormatter INPUT_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OUTPUT_TS = DateTimeFormatter.ofPattern("dd-MMM-yy HH:mm", Locale.ENGLISH);

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private PermissionCacheService permissionCacheService;

    @Autowired
    private ModuleTypeMapper moduleTypeMapper;

    @Autowired
    private ModuleMapper moduleMapper;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @Override
    public ResponseMessage<BaseResult> list(UserGroupListRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            RoleListFilter filter = toRoleListFilter(request);
            Long actorUserId = currentUserId();
            boolean isSuperAdmin = isSuperAdmin(actorUserId);
            filter.setHospitalId(null);

            Pagination pagination = PaginationHelper.buildAndApplyOffset(
                    filter,
                    roleMapper.countUserGroups(filter, null, isSuperAdmin, UserGroupType.HOSPITAL.name(), false)
            );

            List<RoleResponse> rows = roleMapper.listUserGroups(filter, null, isSuperAdmin, UserGroupType.HOSPITAL.name(), false);
            List<UserGroupResponse> responses = mapUserGroupRows(rows, isSuperAdmin, false);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.UserGroup.BASE_PATH + ApiConstants.UserGroup.LIST_PATH, null, null, "User Group", "User Group (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", responses, pagination, true));
        } catch (Exception e) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = extractErrorLine(e);
            activityLogService.insert(ApiConstants.UserGroup.BASE_PATH + ApiConstants.UserGroup.LIST_PATH, errorLine, e.toString(), "User Group", "User Group (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> add(UserGroupCreateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return createInternal(request, httpServletRequest);
    }

    @Override
    public ResponseMessage<BaseResult> findById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            RoleResponse role = getRoleAccessibleOrNull(id, currentUserId(), currentHospitalId());
            if (role == null) {
                return ResponseMessageUtils.makeResponse(false, 404, "NOT_FOUND", "User group not found.");
            }

            List<UserGroupResponse> responses = mapUserGroupRows(List.of(role), isSuperAdmin(currentUserId()), isSuperAdmin(currentUserId()));
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.UserGroup.BASE_PATH + ApiConstants.UserGroup.FIND_PATH, null, null, "User Group", "User Group (Find)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", responses, true));
        } catch (Exception e) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = extractErrorLine(e);
            activityLogService.insert(ApiConstants.UserGroup.BASE_PATH + ApiConstants.UserGroup.FIND_PATH, errorLine, e.toString(), "User Group", "User Group (Find)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private ResponseMessage<BaseResult> createInternal(UserGroupCreateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }

            Long actorUserId = currentUserId();
            Long targetHospitalId = null;

            String name = request.getName().trim();
            if (roleMapper.checkDuplicate(name, null, targetHospitalId) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate", false));
            }
            applyPublicKeyRelations(request);

            Role role = new Role();
            role.setName(name);
            role.setHospitalId(targetHospitalId);
            role.setCreatedBy(actorUserId);
            role.setIsActive(1);

            Boolean created = roleMapper.createRole(role);
            if (created == null || !created || role.getId() == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
            }

            List<Long> beforeUserIds = rolePermissionMapper.findUserIdsByRoleId(role.getId());
            replaceRoleUsers(role.getId(), request.getUserIds());
            replaceRolePermissions(
                    role.getId(),
                    resolveRequestedModuleDetailIds(request.getModuleDetailIds(), request.getModuleTypeList()),
                    actorUserId
            );
            Set<Long> impactedUserIds = new LinkedHashSet<>();
            if (beforeUserIds != null) {
                impactedUserIds.addAll(beforeUserIds);
            }
            List<Long> afterUserIds = rolePermissionMapper.findUserIdsByRoleId(role.getId());
            if (afterUserIds != null) {
                impactedUserIds.addAll(afterUserIds);
            }
            bumpPermissionVersion(new ArrayList<>(impactedUserIds));

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.UserGroup.BASE_PATH + ApiConstants.UserGroup.ADD_PATH, null, null, "User Group", "User Group (Add)", "Add", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
        } catch (Exception e) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = extractErrorLine(e);
            activityLogService.insert(ApiConstants.UserGroup.BASE_PATH + ApiConstants.UserGroup.ADD_PATH, errorLine, e.toString(), "User Group", "User Group (Add)", "Add", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> update(UserGroupUpdateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            request.setId(publicEntityKeyResolver.resolve(Entity.ROLE, request.getPublicKey(), request.getId()));
            if (request.getId() == null || request.getId() <= 0 || request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }

            Long actorUserId = currentUserId();
            boolean isSuperAdmin = isSuperAdmin(actorUserId);
            RoleResponse existing = getRoleAccessibleOrNull(request.getId(), actorUserId, null);
            if (existing == null) {
                return ResponseMessageUtils.makeResponse(false, 404, "NOT_FOUND", "User group not found.");
            }
            if (isProtectedScopeRole(existing.getId()) && !isSuperAdmin) {
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden");
            }

            String name = request.getName().trim();
            if (roleMapper.checkDuplicate(name, request.getId(), null) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate", false));
            }
            applyPublicKeyRelations(request);

            Role role = new Role();
            role.setId(existing.getId());
            role.setHospitalId(null);
            role.setName(name);
            role.setModifiedBy(actorUserId);
            role.setIsActive(1);

            Boolean updated = roleMapper.updateRole(role);
            if (updated == null || !updated) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
            }

            List<Long> beforeUserIds = rolePermissionMapper.findUserIdsByRoleId(role.getId());
            replaceRoleUsers(role.getId(), request.getUserIds());
            replaceRolePermissions(
                    role.getId(),
                    resolveRequestedModuleDetailIds(request.getModuleDetailIds(), request.getModuleTypeList()),
                    actorUserId
            );
            Set<Long> impactedUserIds = new LinkedHashSet<>();
            if (beforeUserIds != null) {
                impactedUserIds.addAll(beforeUserIds);
            }
            List<Long> afterUserIds = rolePermissionMapper.findUserIdsByRoleId(role.getId());
            if (afterUserIds != null) {
                impactedUserIds.addAll(afterUserIds);
            }
            bumpPermissionVersion(new ArrayList<>(impactedUserIds));

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.UserGroup.BASE_PATH + ApiConstants.UserGroup.UPDATE_PATH, null, null, "User Group", "User Group (Update)", "Edit", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
        } catch (Exception e) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = extractErrorLine(e);
            activityLogService.insert(ApiConstants.UserGroup.BASE_PATH + ApiConstants.UserGroup.UPDATE_PATH, errorLine, e.toString(), "User Group", "User Group (Update)", "Edit", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> delete(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (id == null || id <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            if (!isSuperAdmin(currentUserId())) {
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden");
            }
            if (isProtectedScopeRole(id)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Protected group cannot be deleted.", false));
            }

            List<Long> impactedUserIds = rolePermissionMapper.findUserIdsByRoleId(id);
            roleMapper.deleteRolePermissions(id, currentHospitalId());
            roleMapper.deleteUserRoleByRoleId(id);
            Boolean deleted = roleMapper.deleteRoleByIdForSuperAdmin(id);
            bumpPermissionVersion(impactedUserIds);

            if (deleted == null || !deleted) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
            }
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.UserGroup.BASE_PATH + ApiConstants.UserGroup.DELETE_PATH, null, null, "User Group", "User Group (Delete)", "Delete", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
        } catch (Exception e) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = extractErrorLine(e);
            activityLogService.insert(ApiConstants.UserGroup.BASE_PATH + ApiConstants.UserGroup.DELETE_PATH, errorLine, e.toString(), "User Group", "User Group (Delete)", "Delete", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private RoleListFilter toRoleListFilter(UserGroupListRequest request) {
        UserGroupListRequest safe = request == null ? new UserGroupListRequest() : request;
        RoleListFilter filter = new RoleListFilter();
        filter.setPage(safe.getPage());
        filter.setRowsPerPage(safe.getRowsPerPage());
        filter.setSearchText(safe.getSearchText());
        filter.setOrderBy(safe.getOrderBy());
        filter.setHospitalId(publicEntityKeyResolver.resolve(Entity.HOSPITAL, safe.getHospitalKey(), null));
        return filter;
    }

    private List<UserGroupResponse> mapUserGroupRows(List<RoleResponse> rows, boolean isSuperAdmin, boolean includeScopeGroupUsers) {
        if (rows == null) {
            return List.of();
        }
        List<ModuleType> moduleTypeTemplate = moduleTypeMapper.listModuleType(buildLargeModuleTypeFilter());
        return rows.stream().map(row -> {
            UserGroupResponse response = new UserGroupResponse();
            response.setId(row.getId());
            response.setPublicKey(row.getPublicKey());
            response.setHospitalId(row.getHospitalId());
            response.setHospitalName(row.getHospitalName());
            response.setName(row.getName());
            response.setCreatedBy(row.getCreatedBy());
            response.setModifiedBy(row.getModifiedBy());
            response.setCreatedDate(formatDate(row.getCreatedDate()));
            response.setModifiedDate(formatDate(row.getModifiedDate()));
            response.setGroupType(row.getHospitalId() == null ? UserGroupType.SYSTEM : UserGroupType.HOSPITAL);

            List<RoleUser> roleUsers;
            if (includeScopeGroupUsers && isSuperAdmin && row.getHospitalId() == null) {
                roleUsers = roleMapper.getUserAllHospitals(row.getId());
            } else {
                Long hospitalId = row.getHospitalId() == null ? currentHospitalId() : row.getHospitalId();
                roleUsers = roleMapper.getUser(row.getId(), hospitalId);
            }
            List<UserGroupUserResponse> users = mapUsers(roleUsers);
            response.setUserList(users);
            response.setUserIds(mapUserIds(users));

            List<ModuleType> moduleTypeList = buildRoleModuleTypes(moduleTypeTemplate, row.getId());
            response.setModuleTypeList(moduleTypeList);
            response.setModuleDetailIds(collectCheckedModuleDetailIds(moduleTypeList));
            return response;
        }).collect(Collectors.toList());
    }

    private RoleResponse getRoleAccessibleOrNull(Long id, Long actorUserId, Long actorHospitalId) {
        List<RoleResponse> rows = roleMapper.getRoleByIdAnyHospital(id);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    private void replaceRoleUsers(Long roleId, List<Long> userIds) {
        if (roleId == null || roleId <= 0) {
            return;
        }
        if (userIds == null) {
            roleMapper.insertRoleUser(roleId, SUPER_ADMIN_USER_ID);
            return;
        }

        roleMapper.deleteUserRoleByRoleId(roleId);
        for (Long userId : withSuperAdminUserId(userIds)) {
            roleMapper.insertRoleUser(roleId, userId);
        }
    }

    private void applyPublicKeyRelations(UserGroupCreateRequest request) {
        if (request == null) {
            return;
        }
        List<Long> userIds = resolveKeyList(Entity.USER, request.getUserKeys(), "User");
        if (userIds != null) {
            request.setUserIds(userIds);
        }
        List<Long> moduleDetailIds = resolveKeyList(Entity.MODULE_DETAIL, request.getModuleDetailKeys(), "Module detail");
        if (moduleDetailIds != null) {
            request.setModuleDetailIds(moduleDetailIds);
        }
    }

    private void applyPublicKeyRelations(UserGroupUpdateRequest request) {
        if (request == null) {
            return;
        }
        List<Long> userIds = resolveKeyList(Entity.USER, request.getUserKeys(), "User");
        if (userIds != null) {
            request.setUserIds(userIds);
        }
        List<Long> moduleDetailIds = resolveKeyList(Entity.MODULE_DETAIL, request.getModuleDetailKeys(), "Module detail");
        if (moduleDetailIds != null) {
            request.setModuleDetailIds(moduleDetailIds);
        }
    }

    private List<Long> resolveKeyList(Entity entity, List<String> keys, String label) {
        if (keys == null) {
            return null;
        }
        Set<Long> normalized = new LinkedHashSet<>();
        for (String key : keys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            normalized.add(publicEntityKeyResolver.resolveRequired(entity, key.trim(), null, label));
        }
        return new ArrayList<>(normalized);
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

    private void replaceRolePermissions(Long roleId, List<Long> moduleDetailIds, Long actorUserId) {
        List<Long> normalizedIds = moduleDetailIds == null ? List.of() : moduleDetailIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));

        if (!normalizedIds.isEmpty()) {
            Long activeCount = permissionMapper.countActiveModuleDetailsByIds(normalizedIds);
            if (activeCount == null || activeCount != normalizedIds.size()) {
                throw new IllegalArgumentException("One or more module detail ids are invalid or inactive.");
            }
        }

        rolePermissionMapper.deleteByRoleId(roleId);
        if (!normalizedIds.isEmpty()) {
            rolePermissionMapper.insertBatch(roleId, normalizedIds, actorUserId);
        }
    }

    private void bumpRoleUsersPermissionVersion(Long roleId) {
        bumpPermissionVersion(rolePermissionMapper.findUserIdsByRoleId(roleId));
    }

    private void bumpPermissionVersion(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds) {
            if (userId == null || userId <= 0) {
                continue;
            }
            rolePermissionMapper.bumpPermissionVersion(userId);
        }
        permissionCacheService.invalidateByUsers(userIds);
    }

    private List<UserGroupUserResponse> mapUsers(List<RoleUser> users) {
        if (users == null) {
            return List.of();
        }
        return users.stream().map(u -> {
            UserGroupUserResponse user = new UserGroupUserResponse();
            user.setId(u.getId());
            user.setPublicKey(u.getPublicKey());
            user.setUsername(u.getUsername());
            return user;
        }).collect(Collectors.toList());
    }

    private List<Long> mapUserIds(List<UserGroupUserResponse> users) {
        if (users == null) {
            return List.of();
        }
        return users.stream()
                .map(UserGroupUserResponse::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<ModuleType> buildRoleModuleTypes(List<ModuleType> moduleTypeTemplate, Long roleId) {
        if (moduleTypeTemplate == null) {
            return List.of();
        }
        List<ModuleType> clones = new ArrayList<>(moduleTypeTemplate.size());
        for (ModuleType source : moduleTypeTemplate) {
            ModuleType target = new ModuleType();
            target.setId(source.getId());
            target.setPublicKey(source.getPublicKey());
            target.setCode(source.getCode());
            target.setName(source.getName());
            target.setNameOther(source.getNameOther());
            target.setGroupCode(source.getGroupCode());
            target.setGroupName(source.getGroupName());
            target.setGroupOrderNo(source.getGroupOrderNo());
            target.setModuleList(moduleMapper.getOneByRoleId(source.getId(), roleId));
            clones.add(target);
        }
        return clones;
    }

    private List<Long> collectCheckedModuleDetailIds(List<ModuleType> moduleTypeList) {
        if (moduleTypeList == null) {
            return List.of();
        }
        return moduleTypeList.stream()
                .filter(Objects::nonNull)
                .flatMap(type -> type.getModuleList() == null ? java.util.stream.Stream.empty() : type.getModuleList().stream())
                .filter(Objects::nonNull)
                .filter(module -> Boolean.TRUE.equals(module.getChecked()))
                .map(module -> module.getId())
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private List<Long> resolveRequestedModuleDetailIds(List<Long> moduleDetailIds, List<UserGroupModuleTypeSelectionRequest> moduleTypeList) {
        List<Long> fromModuleTypeList = extractCheckedModuleDetailIds(moduleTypeList);
        if (!fromModuleTypeList.isEmpty()) {
            return fromModuleTypeList;
        }
        if (moduleDetailIds == null || moduleDetailIds.isEmpty()) {
            return List.of();
        }
        return moduleDetailIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
    }

    private List<Long> extractCheckedModuleDetailIds(List<UserGroupModuleTypeSelectionRequest> moduleTypeList) {
        if (moduleTypeList == null || moduleTypeList.isEmpty()) {
            return List.of();
        }
        return moduleTypeList.stream()
                .filter(Objects::nonNull)
                .flatMap(type -> type.getModuleList() == null ? java.util.stream.Stream.empty() : type.getModuleList().stream())
                .filter(Objects::nonNull)
                .filter(module -> Boolean.TRUE.equals(module.getChecked()))
                .map(this::resolveModuleDetailSelectionId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
    }

    private Long resolveModuleDetailSelectionId(UserGroupModuleSelectionRequest module) {
        if (module == null) {
            return null;
        }
        return publicEntityKeyResolver.resolve(Entity.MODULE_DETAIL, module.getPublicKey(), module.getId());
    }

    private ModuleTypeFilter buildLargeModuleTypeFilter() {
        ModuleTypeFilter filter = new ModuleTypeFilter();
        filter.setPage(1);
        filter.setRowsPerPage(MAX_ROLE_FETCH_SIZE);
        PaginationHelper.buildAndApplyOffset(filter);
        return filter;
    }


    private boolean isProtectedScopeRole(Long roleId) {
        Long count = roleMapper.countRoleByIdAndCode(roleId, CROSS_HOSPITAL_USER_SCOPE_ROLE_CODE);
        return count != null && count > 0;
    }

    private static boolean isSuperAdmin(Long userId) {
        return SUPER_ADMIN_USER_ID.equals(userId);
    }

    private static String formatDate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return LocalDateTime.parse(value, INPUT_TS).format(OUTPUT_TS);
        } catch (Exception ex) {
            LOGGER.debug("Timestamp format failed for value '{}': {}", value, ex.getMessage());
            return value;
        }
    }

    private static Long extractErrorLine(Exception error) {
        return (error.getStackTrace() != null && error.getStackTrace().length > 0)
                ? (long) error.getStackTrace()[0].getLineNumber()
                : null;
    }

    private static Long currentHospitalId() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null || principal.hospitalId() == null) {
            throw new IllegalStateException("Hospital context not found in OAuth2 token claims.");
        }
        return principal.hospitalId();
    }

    private static Long currentUserId() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null || principal.userId() == null) {
            throw new IllegalStateException("User context not found in OAuth2 token claims.");
        }
        return principal.userId();
    }
}
