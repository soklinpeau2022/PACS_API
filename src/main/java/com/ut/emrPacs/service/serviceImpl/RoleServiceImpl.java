package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.helper.FunctionHelper;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.cache.config.CacheConfig;
import com.ut.emrPacs.cache.permission.PermissionCacheService;
import com.ut.emrPacs.helper.CommonHelper;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.permission.PermissionMapper;
import com.ut.emrPacs.mapper.permission.RolePermissionMapper;
import com.ut.emrPacs.model.dto.response.authentication.module.MenuGroupResponse;
import com.ut.emrPacs.model.role.Role;
import com.ut.emrPacs.model.role.Module;
import com.ut.emrPacs.mapper.user.ModuleMapper;
import com.ut.emrPacs.mapper.user.ModuleTypeMapper;
import com.ut.emrPacs.mapper.user.RoleMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.ModuleTypeFilter;
import com.ut.emrPacs.model.base.filter.RoleListFilter;
import com.ut.emrPacs.model.dto.request.authentication.role.RoleCreateRequest;
import com.ut.emrPacs.model.dto.request.authentication.role.RoleDataUpdate;
import com.ut.emrPacs.model.dto.response.authentication.module.ModuleType;
import com.ut.emrPacs.model.dto.response.authentication.role.RoleResponse;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.RoleService;
import com.ut.emrPacs.service.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl implements RoleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoleServiceImpl.class);
    private static final Long SUPER_ADMIN_USER_ID = 1L;
    private static final String CROSS_HOSPITAL_USER_SCOPE_ROLE_CODE = "USER_HOSPITAL_SCOPE_ALL";

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private ModuleTypeMapper moduleTypeMapper;

    @Autowired
    private ModuleMapper moduleMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private CommonHelper commonHelper;

    @Autowired
    private PermissionCacheService permissionCacheService;

    @Autowired(required = false)
    private CacheManager cacheManager;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;


    /** Service implementation method. */
    public ResponseMessage<BaseResult> listRole(RoleListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load role and return API response.
        LocalTime startDuration = LocalTime.now();

        try {
            RoleListFilter safeFilter = filter == null ? new RoleListFilter() : filter;
            Long actorUserId = currentUserId();
            boolean isSuperAdmin = isSuperAdmin(actorUserId);
            safeFilter.setHospitalId(null);
            safeFilter.setStrictHospitalOnly(false);
            // Private security group: visible/manageable only by super admin.
            safeFilter.setHideCrossHospitalScopeGroup(!isSuperAdmin);

            Pagination pagination = PaginationHelper.buildAndApplyOffset(
                    safeFilter,
                    roleMapper.countList(safeFilter, null)
            );

            List<RoleResponse> roleList = roleMapper.listRole(safeFilter, null);
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Role.BASE_PATH + ApiConstants.Role.LIST_PATH,null,null,"System Role","System Role (View)","View",1,"Success",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", roleList, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Role.BASE_PATH + ApiConstants.Role.LIST_PATH, errorLine, error.toString(),"System Role","System Role (View)","View",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    /** Service implementation method. */
    public ResponseMessage<BaseResult> getRoleById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load role by id and return API response.
        LocalTime startDuration = LocalTime.now();
        try {
            Long actorUserId = currentUserId();
            if (isCrossHospitalScopeRole(id) && !isSuperAdmin(actorUserId)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Forbidden.", false));
            }
            List<RoleResponse> roles = roleMapper.getRoleById(id, null);
            if(!roles.isEmpty()){
                Long roleId = roles.get(0).getId();
                roles.get(0).setUserList(roleMapper.getUser(roleId, null));

                ModuleTypeFilter moduleTypeFilter = new ModuleTypeFilter();
                moduleTypeFilter.setPage(1);
                moduleTypeFilter.setRowsPerPage(100);
                PaginationHelper.buildAndApplyOffset(moduleTypeFilter);
                List<ModuleType> moduleTypeList = moduleTypeMapper.listModuleType(moduleTypeFilter);
                List<ModuleType> moduleTypeListForGroupSummary = moduleTypeList;
                if(moduleTypeList != null){
                    for (ModuleType moduleType : moduleTypeList) {
                        Long moduleTypeId = moduleType.getId();
                        moduleType.setModuleList(moduleMapper.getOneByRoleId(moduleTypeId, roleId));
                    }
                    moduleTypeList = moduleTypeList.stream()
                            .filter(mt -> mt.getModuleList() != null && !mt.getModuleList().isEmpty())
                            .collect(Collectors.toList());
                }
                roles.get(0).setModuleTypeList(moduleTypeList);
                BaseResult baseResult = messageService.message("Success", roles, true);
                baseResult.setMenuGroupList(buildMenuGroupsFromDb(moduleTypeListForGroupSummary, true));
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.Role.BASE_PATH + ApiConstants.Role.FIND_PATH,null,null,"System Role","System Role (View)","View",1,"Success",startDuration,endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, baseResult);
            }
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Role.BASE_PATH + ApiConstants.Role.FIND_PATH,null,null,"System Role","System Role (View)","View",1,"Success",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", roles, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Role.BASE_PATH + ApiConstants.Role.FIND_PATH, errorLine, error.toString(),"System Role","System Role (View)","View",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    /** Service implementation method. */
    @Transactional(rollbackFor = Exception.class)
    public ResponseMessage<BaseResult> createRole(RoleCreateRequest roleData, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Insert flow: validate request, create role, and return operation result.
        LocalTime startDuration = LocalTime.now();
        try {
            Long userId = userService.getUserAuth().getId();
            Long hospitalId = null;

            if(roleData == null || roleData.getName() == null || roleData.getName().trim().isEmpty()){
                return ResponseMessageUtils.makeResponse(false, messageService.message("Required", false));
            }
            roleData.setName(roleData.getName().trim());

            if(roleMapper.checkDuplicate(roleData.getName(), null, hospitalId) > 0){
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate", false));
            }
            applyPublicKeyRelations(roleData);
            List<Long> requestedUserIds = normalizePositiveIds(roleData.getUserIds());
            List<Long> requestedModuleDetailIds = normalizePositiveIds(roleData.getModuleDetailIds());

            String userValidationError = validateRoleUsersInCurrentHospital(requestedUserIds);
            if (userValidationError != null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(userValidationError, false));
            }
            String permissionValidationError = validateModuleDetails(requestedModuleDetailIds);
            if (permissionValidationError != null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(permissionValidationError, false));
            }

            Role role = new Role();
            role.setName(roleData.getName());
            role.setCreatedBy(userId);
            role.setHospitalId(hospitalId);
            role.setIsActive(1);

            // Create role
            Boolean result = roleMapper.createRole(role);
            if (result) {
                String applyUserResult = insertRoleUser(requestedUserIds, role.getId());
                if (applyUserResult != null) {
                    throw new IllegalStateException(applyUserResult);
                }
                if(requestedModuleDetailIds != null) {
                    String permissionError = insertRolePermission(requestedModuleDetailIds, role.getId(), userId);
                    if (permissionError != null) {
                        throw new IllegalStateException(permissionError);
                    }
                }
                List<Long> impactedUserIds = rolePermissionMapper.findUserIdsByRoleId(role.getId());
                bumpPermissionVersion(impactedUserIds);
                refreshRoleCaches(impactedUserIds);
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.Role.BASE_PATH + ApiConstants.Role.CREATE_PATH,null,null,"System Role","System Role (Add)","Add",1,"Success",startDuration,endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            } else {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
            }
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Role.BASE_PATH + ApiConstants.Role.CREATE_PATH, errorLine, error.toString(),"System Role","System Role (Add)","Add",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }


    /** Service implementation method. */
    @Transactional(rollbackFor = Exception.class)
    public ResponseMessage<BaseResult> updateRole(RoleDataUpdate roleDataUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Update flow: validate request, apply changes to role, and return operation result.
        LocalTime startDuration = LocalTime.now();
        try {
            if (roleDataUpdate == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            roleDataUpdate.setId(publicEntityKeyResolver.resolve(Entity.ROLE, roleDataUpdate.getPublicKey(), roleDataUpdate.getId()));
            if (roleDataUpdate.getId() == null || roleDataUpdate.getId() <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            if (roleDataUpdate.getName() == null || roleDataUpdate.getName().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Required", false));
            }
            roleDataUpdate.setName(roleDataUpdate.getName().trim());

            Long hospitalId = null;
            Long actorUserId = currentUserId();
            if (isCrossHospitalScopeRole(roleDataUpdate.getId()) && !isSuperAdmin(actorUserId)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Forbidden.", false));
            }
            List<RoleResponse> existingRole = roleMapper.getRoleById(roleDataUpdate.getId(), hospitalId);
            if (existingRole == null || existingRole.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Role not found.", false));
            }
            if(roleMapper.checkDuplicate(roleDataUpdate.getName(), roleDataUpdate.getId(), hospitalId) > 0){
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate", false));
            }
            applyPublicKeyRelations(roleDataUpdate);
            List<Long> requestedUserIds = normalizePositiveIds(roleDataUpdate.getUserIds());
            List<Long> requestedModuleDetailIds = normalizePositiveIds(roleDataUpdate.getModuleDetailIds());

            String userValidationError = validateRoleUsersInCurrentHospital(requestedUserIds);
            if (userValidationError != null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(userValidationError, false));
            }
            String permissionValidationError = validateModuleDetails(requestedModuleDetailIds);
            if (permissionValidationError != null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(permissionValidationError, false));
            }

            Role role = new Role();
            role.setId(roleDataUpdate.getId());
            role.setHospitalId(hospitalId);
            role.setName(roleDataUpdate.getName());
            role.setIsActive(1);
            role.setModifiedBy(userService.getUserAuth().getId());

            // Update role
            Boolean result = roleMapper.updateRole(role);
            if (result) {
                Set<Long> impactedUserIds = new HashSet<>();
                List<Long> beforeUserIds = rolePermissionMapper.findUserIdsByRoleId(role.getId());
                if (beforeUserIds != null) {
                    impactedUserIds.addAll(beforeUserIds);
                }
                String applyUserResult = insertRoleUser(requestedUserIds, role.getId());
                if (applyUserResult != null) {
                    throw new IllegalStateException(applyUserResult);
                }
                String permissionError = insertRolePermission(requestedModuleDetailIds, role.getId(), role.getModifiedBy());
                if (permissionError != null) {
                    throw new IllegalStateException(permissionError);
                }
                List<Long> afterUserIds = rolePermissionMapper.findUserIdsByRoleId(role.getId());
                if (afterUserIds != null) {
                    impactedUserIds.addAll(afterUserIds);
                }
                bumpPermissionVersion(new ArrayList<>(impactedUserIds));
                refreshRoleCaches(new ArrayList<>(impactedUserIds));

                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.Role.BASE_PATH + ApiConstants.Role.UPDATE_PATH,null,null,"System Role","System Role (Edit)","Edit",1,"Success",startDuration,endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            } else {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
            }
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Role.BASE_PATH + ApiConstants.Role.UPDATE_PATH, errorLine, error.toString(),"System Role","System Role (Edit)","Edit",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    /** Service implementation method. */
    @Transactional(rollbackFor = Exception.class)
    public ResponseMessage<BaseResult> deleteRole(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Delete flow: validate request, remove or deactivate role, and return operation result.
        LocalTime startDuration = LocalTime.now();
        try {
            if (id == null || id <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            Long actorUserId = currentUserId();
            if (!isSuperAdmin(actorUserId)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Forbidden.", false));
            }
            if (isCrossHospitalScopeRole(id) && !isSuperAdmin(actorUserId)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Forbidden.", false));
            }

            // Delete role
            List<RoleResponse> existingRole = roleMapper.getRoleById(id, null);
            if (existingRole == null || existingRole.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Role not found.", false));
            }
            if (Boolean.TRUE.equals(existingRole.get(0).getIsSystemRole())) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Protected role cannot be deleted.", false));
            }
            Boolean result = roleMapper.deleteRole(id, null);
            if (result) {
                List<Long> impactedUserIds = rolePermissionMapper.findUserIdsByRoleId(id);
                roleMapper.deleteRolePermissions(id, null);
                roleMapper.deleteUserRoleByRoleId(id);
                bumpPermissionVersion(impactedUserIds);
                refreshRoleCaches(impactedUserIds);
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.Role.BASE_PATH + ApiConstants.Role.DELETE_PATH,null,null,"System Role","System Role (Delete)","Delete",1,"Success",startDuration,endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            } else {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
            }
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Role.BASE_PATH + ApiConstants.Role.DELETE_PATH, errorLine, error.toString(),"System Role","System Role (Delete)","Delete",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    /** Service implementation method. */
    public ResponseMessage<BaseResult> menu(HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load records and return API response.
        LocalTime startDuration = LocalTime.now();
        try {
            Long userId = userService.getUserAuth().getId();
            List<ModuleType> roleList = roleMapper.getModule(userId, null);

            if(roleList != null && !roleList.isEmpty()){
                for (ModuleType moduleType : roleList) {
                    Long moduleTypeId = moduleType.getId();
                    moduleType.setModuleList(moduleMapper.getOneByUserId(moduleTypeId, userId));
                }
            }

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Role.MENU_FULL_PATH,null,null,"System Role","System Role (Menu)","View",1,"Success",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", roleList,true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Role.MENU_FULL_PATH, errorLine, error.toString(),"System Role","System Role (Menu)","View",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private String insertRoleUser(List<Long> userIds, Long roleId){
        // Insert flow: validate request, create role user, and return operation result.
        if (roleId == null || roleId <= 0) {
            return "Role id is required.";
        }
        if (userIds == null) {
            roleMapper.insertRoleUser(roleId, SUPER_ADMIN_USER_ID);
            return null;
        }
        commonHelper.replaceRelations(
                () -> roleMapper.deleteUserRoleByRoleId(roleId),
                withSuperAdminUserId(userIds),
                userId -> userId != null && userId > 0,
                userId -> roleMapper.insertRoleUser(roleId, userId)
        );
        return null;
    }

    private static List<Long> withSuperAdminUserId(List<Long> userIds) {
        Set<Long> normalized = new LinkedHashSet<>();
        normalized.add(SUPER_ADMIN_USER_ID);
        if (userIds != null) {
            userIds.stream()
                    .filter(id -> id != null && id > 0)
                    .forEach(normalized::add);
        }
        return new ArrayList<>(normalized);
    }

    private String insertRolePermission(List<Long> moduleDetailIds, Long roleId, Long actorUserId){
        // Replace role permissions using validated module_detail ids.
        if (moduleDetailIds == null) {
            return null;
        }
        List<Long> normalizedModuleDetailIds = normalizePositiveIds(moduleDetailIds);

        if (!normalizedModuleDetailIds.isEmpty()) {
            Long activeCount = permissionMapper.countActiveModuleDetailsByIds(normalizedModuleDetailIds);
            if (activeCount == null || activeCount != normalizedModuleDetailIds.size()) {
                return "One or more module detail ids are invalid or inactive.";
            }
        }

        rolePermissionMapper.deleteByRoleId(roleId);
        if (!normalizedModuleDetailIds.isEmpty()) {
            rolePermissionMapper.insertBatch(roleId, normalizedModuleDetailIds, actorUserId);
        }
        return null;
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
    }

    private void refreshRoleCaches(List<Long> impactedUserIds) {
        permissionCacheService.invalidateByUsers(impactedUserIds);
        evictCache(CacheConfig.DROPDOWN_USER_GROUPS);
    }

    private void evictCache(String cacheName) {
        if (cacheManager == null || cacheName == null || cacheName.isBlank()) {
            return;
        }
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    /** Service implementation method. */
    public ResponseMessage<BaseResult> listRoleUserGroupl(RoleListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load role list with userList only and return API response.
        LocalTime startDuration = LocalTime.now();

        try {
            RoleListFilter safeFilter = filter == null ? new RoleListFilter() : filter;
            Long actorUserId = currentUserId();
            boolean isSuperAdmin = isSuperAdmin(actorUserId);
            safeFilter.setHospitalId(null);
            safeFilter.setStrictHospitalOnly(false);
            safeFilter.setHideCrossHospitalScopeGroup(!isSuperAdmin);

            Pagination pagination = PaginationHelper.buildAndApplyOffset(
                    safeFilter,
                    roleMapper.countList(safeFilter, null)
            );

            List<RoleResponse> roleList = roleMapper.listRole(safeFilter, null);
            if (!roleList.isEmpty()) {
                for (RoleResponse role : roleList) {
                    role.setUserList(roleMapper.getUser(role.getId(), null));
                    role.setModuleTypeList(null);
                }
            }

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Role.BASE_PATH + ApiConstants.Role.USER_GROUP_LIST_PATH,null,null,"System Role","System Role (View)","View",1,"Success",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", roleList, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Role.BASE_PATH + ApiConstants.Role.USER_GROUP_LIST_PATH, errorLine, error.toString(),"System Role","System Role (View)","View",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private static List<Long> normalizePositiveIds(List<Long> ids) {
        if (ids == null) {
            return null;
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
    }

    private void applyPublicKeyRelations(RoleDataUpdate request) {
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

    private void applyPublicKeyRelations(RoleCreateRequest request) {
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

    private static Long currentUserId() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null || principal.userId() == null) {
            throw new IllegalStateException("User context not found in OAuth2 token claims.");
        }
        return principal.userId();
    }

    private static boolean isSuperAdmin(Long userId) {
        return SUPER_ADMIN_USER_ID.equals(userId);
    }

    private String validateRoleUsersInCurrentHospital(List<Long> userIds) {
        if (userIds == null) {
            return null;
        }
        List<Long> normalizedIds = normalizePositiveIds(userIds);
        if (normalizedIds.isEmpty()) {
            return null;
        }
        Long hospitalId = null;
        try {
            var principal = UserAuthSession.getCurrentUser();
            hospitalId = principal != null ? principal.hospitalId() : null;
        } catch (Exception ex) {
            LOGGER.debug("Could not resolve hospital from security context: {}", ex.getMessage());
        }

        Long activeCount = (hospitalId != null)
                ? roleMapper.countActiveUsersByIdsInHospital(normalizedIds, hospitalId)
                : roleMapper.countActiveUsersByIds(normalizedIds);
        if (activeCount == null || activeCount != normalizedIds.size()) {
            return "One or more user ids are invalid, inactive, or outside your hospital scope.";
        }
        return null;
    }

    private String validateModuleDetails(List<Long> moduleDetailIds) {
        if (moduleDetailIds == null) {
            return null;
        }
        List<Long> normalizedIds = normalizePositiveIds(moduleDetailIds);

        if (normalizedIds.isEmpty()) {
            return null;
        }
        Long activeCount = permissionMapper.countActiveModuleDetailsByIds(normalizedIds);
        if (activeCount == null || activeCount != normalizedIds.size()) {
            return "One or more module detail ids are invalid or inactive.";
        }
        return null;
    }

    private boolean isCrossHospitalScopeRole(Long roleId) {
        if (roleId == null || roleId <= 0) {
            return false;
        }
        Long count = roleMapper.countRoleByIdAndCode(roleId, CROSS_HOSPITAL_USER_SCOPE_ROLE_CODE);
        return count != null && count > 0;
    }

    private static List<MenuGroupResponse> buildMenuGroupsFromDb(List<ModuleType> moduleTypes, boolean withCounts) {
        Map<String, MenuGroupResponse> groupsByCode = new LinkedHashMap<>();
        if (moduleTypes == null || moduleTypes.isEmpty()) {
            return new ArrayList<>();
        }

        for (ModuleType moduleType : moduleTypes) {
            if (moduleType == null || isBlank(moduleType.getGroupCode())) {
                continue;
            }

            MenuGroupResponse group = groupsByCode.computeIfAbsent(moduleType.getGroupCode(), groupCode -> {
                MenuGroupResponse response = new MenuGroupResponse();
                response.setGroupCode(groupCode);
                response.setGroupName(moduleType.getGroupName());
                response.setOrderNo(moduleType.getGroupOrderNo());
                if (withCounts) {
                    response.setCheckedCount(0L);
                    response.setTotalCount(0L);
                }
                return response;
            });

            if (withCounts && moduleType.getModuleList() != null) {
                for (Module module : moduleType.getModuleList()) {
                    if (module == null) {
                        continue;
                    }
                    group.setTotalCount(group.getTotalCount() + 1);
                    if (Boolean.TRUE.equals(module.getChecked())) {
                        group.setCheckedCount(group.getCheckedCount() + 1);
                    }
                }
            }
        }

        List<MenuGroupResponse> groups = new ArrayList<>(groupsByCode.values());
        groups.sort(Comparator
                .comparing((MenuGroupResponse item) -> item.getOrderNo() == null ? Integer.MAX_VALUE : item.getOrderNo())
                .thenComparing(item -> item.getGroupCode() == null ? "" : item.getGroupCode()));
        return groups;
    }

    private static boolean isBlank(String value) {
        return !FunctionHelper.hasText(value);
    }

}

