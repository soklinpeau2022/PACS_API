package com.ut.emrPacs.service.serviceImpl;
import com.ut.emrPacs.helper.pagination.PaginationHelper;

import com.ut.emrPacs.authentication.helper.UserAuthHelper;
import com.ut.emrPacs.cache.permission.PermissionCacheService;
import com.ut.emrPacs.authentication.util.PasswordPolicy;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.helper.security.SecurityAuditLogger;
import com.ut.emrPacs.mapper.user.ModuleMapper;
import com.ut.emrPacs.mapper.user.ModuleTypeMapper;
import com.ut.emrPacs.mapper.permission.RolePermissionMapper;
import com.ut.emrPacs.mapper.user.RoleMapper;
import com.ut.emrPacs.mapper.user.UserMapper;
import com.ut.emrPacs.model.base.*;
import com.ut.emrPacs.model.base.filter.ModuleTypeFilter;
import com.ut.emrPacs.model.base.filter.RoleListFilter;
import com.ut.emrPacs.model.base.filter.UserListFilter;
import com.ut.emrPacs.model.dto.request.authentication.changePassword.ChangePasswordRequest;
import com.ut.emrPacs.model.dto.request.authentication.user.EditProfileRequest;
import com.ut.emrPacs.model.dto.request.authentication.user.UserUpdateRequest;
import com.ut.emrPacs.model.dto.response.authentication.module.ModuleType;
import com.ut.emrPacs.model.dto.response.authentication.role.RoleResponse;
import com.ut.emrPacs.model.dto.response.authentication.user.UserGroupListResponse;
import com.ut.emrPacs.model.dto.response.authentication.user.UserGroupUserResponse;
import com.ut.emrPacs.model.dto.response.authentication.user.UserResponse;
import com.ut.emrPacs.model.users.User;
import com.ut.emrPacs.model.users.UserRequest;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final Long SUPER_ADMIN_USER_ID = 1L;
    private static final String CROSS_HOSPITAL_USER_SCOPE_ROLE_CODE = "USER_HOSPITAL_SCOPE_ALL";
    private static final DateTimeFormatter INPUT_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OUTPUT_TS = DateTimeFormatter.ofPattern("dd-MMM-yy HH:mm", Locale.ENGLISH);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ModuleMapper moduleMapper;

    @Autowired
    private ModuleTypeMapper moduleTypeMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private PermissionCacheService permissionCacheService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ActivityLogService activityLogService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);


    /** Service implementation method. */
    public ResponseMessage<BaseResult> listUser(UserListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load user and return API response.
        LocalTime startDuration = LocalTime.now();
        try {
            UserListFilter safeFilter = filter == null ? new UserListFilter() : filter;
            Long actorUserId = currentUserId();
            Long actorHospitalId = currentHospitalId();
            boolean canViewAllHospitals = canAccessAllUserHospitals(actorUserId);

            // Security scope:
            // - user id=1 can view all hospitals and optionally filter by hospitalId.
            // - other users are always restricted to their own login hospital.
            if (!canViewAllHospitals) {
                safeFilter.setHospitalId(actorHospitalId);
            } else if (safeFilter.getHospitalId() != null && safeFilter.getHospitalId() <= 0) {
                safeFilter.setHospitalId(null);
            }

            Pagination pagination = PaginationHelper.buildAndApplyOffset(
                    safeFilter,
                    userMapper.countList(safeFilter)
            );

            List<UserResponse> userList = userMapper.listUser(safeFilter);
            if (userList != null && !userList.isEmpty()) {
                for (UserResponse userResponse : userList) {
                    if (userResponse == null) continue;
                    userResponse.setUserRoleList(userMapper.getOneUserGroupList(userResponse.getId()));
                }
            }
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.LIST_PATH,null,null,"User","User (View)","View",1,"Success",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", userList, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.LIST_PATH, errorLine, error.toString(),"User","User (View)","View",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    /** Service implementation method. */
    @Override
    public ResponseMessage<BaseResult> listUserGroup(UserListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            UserListFilter safeFilter = filter == null ? new UserListFilter() : filter;
            Long actorUserId = currentUserId();
            Long actorHospitalId = currentHospitalId();
            boolean canViewAllHospitals = canAccessAllUserHospitals(actorUserId);
            boolean isSuperAdmin = isSuperAdmin(actorUserId);

            if (!canViewAllHospitals) {
                safeFilter.setHospitalId(actorHospitalId);
            } else if (safeFilter.getHospitalId() != null && safeFilter.getHospitalId() <= 0) {
                safeFilter.setHospitalId(null);
            }

            RoleListFilter roleFilter = new RoleListFilter();
            roleFilter.setPage(safeFilter.getPage());
            roleFilter.setRowsPerPage(safeFilter.getRowsPerPage());
            roleFilter.setSearchText(safeFilter.getSearchText());
            roleFilter.setOrderBy(safeFilter.getOrderBy());
            roleFilter.setHospitalId(safeFilter.getHospitalId());
            // For privacy: normal users must only see groups in their own hospital, never global/system scope group.
            roleFilter.setStrictHospitalOnly(!canViewAllHospitals || safeFilter.getHospitalId() != null);
            roleFilter.setHideCrossHospitalScopeGroup(!isSuperAdmin);

            Pagination pagination = PaginationHelper.buildAndApplyOffset(
                    roleFilter,
                    roleMapper.countList(roleFilter, actorHospitalId)
            );

            List<RoleResponse> roleRows = roleMapper.listRole(roleFilter, actorHospitalId);
            if (roleRows == null) {
                roleRows = new ArrayList<>();
            }
            List<UserGroupListResponse> groups = roleRows.stream().map(role -> {
                UserGroupListResponse row = new UserGroupListResponse();
                row.setId(role.getId());
                row.setPublicKey(role.getPublicKey());
                row.setName(role.getName());
                row.setCreatedBy(role.getCreatedBy());
                row.setModifiedBy(role.getModifiedBy());
                row.setCreatedDate(formatGroupDate(role.getCreatedDate()));
                row.setModifiedDate(formatGroupDate(role.getModifiedDate()));

                List<com.ut.emrPacs.model.role.RoleUser> roleUsers;
                if (isSuperAdmin && role.getHospitalId() == null) {
                    roleUsers = roleMapper.getUserAllHospitals(role.getId());
                } else {
                    Long groupHospitalId = role.getHospitalId() != null ? role.getHospitalId() : actorHospitalId;
                    roleUsers = roleMapper.getUser(role.getId(), groupHospitalId);
                }
                if (roleUsers == null) {
                    roleUsers = new ArrayList<>();
                }
                List<UserGroupUserResponse> users = roleUsers.stream().map(u -> {
                    UserGroupUserResponse item = new UserGroupUserResponse();
                    item.setId(u.getId());
                    item.setUsername(u.getUsername());
                    return item;
                }).collect(Collectors.toList());
                row.setUserList(users);
                return row;
            }).collect(Collectors.toList());

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.GROUP_LIST_PATH,null,null,"User","User Group (View)","View",1,"Success",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", groups, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.GROUP_LIST_PATH, errorLine, error.toString(),"User","User Group (View)","View",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }


    /** Service implementation method. */
    public ResponseMessage<BaseResult> createUser(UserRequest userRequest, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Insert flow: validate request, create user, and return operation result.
        LocalTime startDuration = LocalTime.now();
        try {

            if (userRequest == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }

            String username = userRequest.getUsername() != null ? userRequest.getUsername().trim() : "";
            if (username.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Username is required.", false));
            }

            String policyError = PasswordPolicy.validate(userRequest.getPassword());
            if (policyError != null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(policyError, false));
            }

            if (userMapper.checkDuplicate(username) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Username", false));
            }

            Long actorUserId = currentUserId();
            Long actorHospitalId = currentHospitalId();
            boolean canViewAllHospitals = canAccessAllUserHospitals(actorUserId);
            applyPublicKeyRelations(userRequest);
            List<Long> targetHospitalIds = normalizeIds(userRequest.getHospitalIds(), userRequest.getHospitalId());
            if (targetHospitalIds.isEmpty()) {
                targetHospitalIds = List.of(actorHospitalId);
            }
            if (!canViewAllHospitals && !containsOnlyHospital(targetHospitalIds, actorHospitalId)) {
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden");
            }
            List<Long> targetUserGroupIds = normalizeIds(userRequest.getUserGroupIds(), userRequest.getUserGroupId());
            if (!isSuperAdmin(actorUserId) && containsProtectedScopeRole(targetUserGroupIds)) {
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden");
            }

            User user = new User();
            user.setUsername(username);
            user.setEmail(userRequest.getEmail() != null ? userRequest.getEmail().trim() : null);
            user.setFirstName(userRequest.getFirstName() != null ? userRequest.getFirstName().trim() : null);
            user.setLastName(userRequest.getLastName() != null ? userRequest.getLastName().trim() : null);
            user.setPassword(encoder.encode(userRequest.getPassword()));
            // Create user
            Boolean result = userMapper.createUser(user);
            if (result) {
                if (user.getId() != null) {
                    replaceUserHospitals(targetHospitalIds, user.getId());
                    replaceUserGroups(targetUserGroupIds, user.getId());
                }

                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.CREATE_PATH,null,null,"User","User (Add)","Add",1,"Success",startDuration,endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            } else {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
            }
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.CREATE_PATH, errorLine, error.toString(),"User","User (Add)","Add",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }

    }

    /** Service implementation method. */
    public ResponseMessage<UserResponse> me(HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load records and return API response.
        LocalTime startDuration = LocalTime.now();
        try {
            Long userId = getUserAuth().getId();

            List<UserResponse> userList = userMapper.getOneByUserId(userId);

            if (userList != null && !userList.isEmpty()) {
                Long loadedUserType = userList.getFirst().getUserType();
                boolean isSystemAdmin = (loadedUserType != null && loadedUserType == 9L)
                        || (UserAuthHelper.getUserAuth() != null &&
                            UserAuthHelper.getUserAuth().getAuthorities().stream()
                                .anyMatch(a -> "ROLE_SYSTEM_ADMIN".equals(a.getAuthority())));

                List<ModuleType> moduleTypeList;
                if (isSystemAdmin) {
                    moduleTypeList = moduleTypeMapper.listModuleType(new ModuleTypeFilter());
                    if (moduleTypeList != null) {
                        for (ModuleType moduleType : moduleTypeList) {
                            List<com.ut.emrPacs.model.role.Module> modules = moduleMapper.getModuleById(moduleType.getId());
                            if (modules != null) {
                                modules.forEach(m -> m.setChecked(true));
                            }
                            moduleType.setModuleList(modules);
                        }
                        moduleTypeList = moduleTypeList.stream()
                                .filter(mt -> mt.getModuleList() != null && !mt.getModuleList().isEmpty())
                                .collect(Collectors.toList());
                    }
                } else {
                    moduleTypeList = roleMapper.getModule(userId, currentHospitalId());
                    if (moduleTypeList != null && !moduleTypeList.isEmpty()) {
                        for (ModuleType moduleType : moduleTypeList) {
                            moduleType.setModuleList(moduleMapper.getOneByUserId(moduleType.getId(), userId));
                        }
                    }
                }
                userList.getFirst().setModuleTypeList(moduleTypeList);
                userList.getFirst().setHospitalList(userMapper.getHospitalIdByUserId(userId));
                userList.getFirst().setUserRoleList(userMapper.getOneUserGroupList(userId));
            }
            if (userList == null || userList.isEmpty()) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.User.ME_FULL_PATH, null, null, "User", "User (Me)", "Me", 2, "Error", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to load your profile. Please try again.", false));
            }


            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.User.ME_FULL_PATH,null,null,"User","User (Me)","Me",1,"Success",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeSuccessResponse(userList.get(0));
        } catch (Exception error) {
            LOGGER.error("user-me failed", error);
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.User.ME_FULL_PATH, errorLine, error.toString(),"User","User (Me)","Me",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }


    /** Service implementation method. */
    public ResponseMessage<BaseResult> editProfile(EditProfileRequest editProfileRequest, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Update flow: validate request, apply changes to profile, and return operation result.
        LocalTime startDuration = LocalTime.now();
        try {

            Long userId = getUserAuth().getId();

            User user = new User();
            user.setId(userId);
            user.setUserType(editProfileRequest.getUserType());
            user.setFirstName(editProfileRequest.getFirstName());
            user.setLastName(editProfileRequest.getLastName());
            user.setTelephone(editProfileRequest.getTelephone());
            user.setSex(editProfileRequest.getSex());
            user.setModifiedBy(userId);
            user.setIsActive(1L);
            Boolean result = userMapper.editProfile(user);
            if (result) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert("/user/edit-profile", null, null, "User", "User (Edit Profile)", "Edit Profile", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            } else {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
            }
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/user/edit-profile", errorLine, error.toString(), "User", "User (Edit Profile)", "Edit Profile", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }



    /** Service implementation method. */
    public ResponseMessage<BaseResult> getUserById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load user by id and return API response.
        LocalTime startDuration = LocalTime.now();
        try {
            Long actorUserId = currentUserId();
            Long actorHospitalId = currentHospitalId();
            boolean canViewAllHospitals = canAccessAllUserHospitals(actorUserId);
            if (!canViewAllHospitals && !isUserInHospital(id, actorHospitalId)) {
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden");
            }

            List<UserResponse> userList = userMapper.getOneByUserId(id);
            if (userList != null && !userList.isEmpty()) {
                for (UserResponse user : userList) {
                    if (user == null) continue;
                    user.setUserRoleList(userMapper.getOneUserGroupList(user.getId()));
                    user.setHospitalList(userMapper.getHospitalIdByUserId(user.getId()));
                }
            }
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.FIND_PATH,null,null,"User","User (View)","View",1,"Success",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", userList, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.FIND_PATH, errorLine, error.toString(),"User","User (View)","View",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<String> changePassword(ChangePasswordRequest changePasswordRequest, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Update flow: validate request, apply changes to password, and return operation result.
        LocalTime startDuration = LocalTime.now();
        try {
            if (changePasswordRequest == null) {
                return ResponseMessageUtils.makeResponse(false, "Invalid request.");
            }

            Long targetUserId = publicEntityKeyResolver.resolve(Entity.USER, changePasswordRequest.getUserKey(), null);
            changePasswordRequest.setUserId(targetUserId);

            // BOLA guard: reject requests with no user public key or invalid target
            if (targetUserId == null || targetUserId <= 0) {
                return ResponseMessageUtils.makeResponse(false, "Invalid user.");
            }

            String newPassword = changePasswordRequest.getNewPassword() != null ? changePasswordRequest.getNewPassword() : "";
            String confirmPassword = changePasswordRequest.getConfirmPassword() != null ? changePasswordRequest.getConfirmPassword() : "";

            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, "New password and confirm password are required.");
            }

            if (!newPassword.equals(confirmPassword)) {
                return ResponseMessageUtils.makeResponse(false, "Passwords do not match.");
            }

            String policyError = PasswordPolicy.validatePasswordChange(newPassword);
            if (policyError != null) {
                return ResponseMessageUtils.makeResponse(false, policyError);
            }

            List<UserResponse> user = userMapper.getOneByUserId(changePasswordRequest.getUserId());
            if (user == null || user.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, "User not found.");
            }
            UserResponse targetUser = user.get(0);
            if (isProtectedPasswordResetUser(targetUser)) {
                SecurityAuditLogger.logBlocked(LOGGER, httpServletRequest, "protected_admin_password_reset", "change_protected_admin_password", String.valueOf(changePasswordRequest.getUserId()));
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Protected admin passwords cannot be changed from Reset Password.");
            }

            Long actorUserId = currentUserId();
            Long actorHospitalId = currentHospitalId();
            boolean canViewAllHospitals = canAccessAllUserHospitals(actorUserId);
            if (!canViewAllHospitals && !isUserInHospital(changePasswordRequest.getUserId(), actorHospitalId)) {
                SecurityAuditLogger.logBlocked(LOGGER, httpServletRequest, "hospital_scope_violation", "change_password_other_hospital", String.valueOf(changePasswordRequest.getUserId()));
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden");
            }

            // BOLA guard: prevent privilege escalation by blocking password change on the super admin account
            // unless the requester IS that same super admin (self password rotation).
            Long callerId = actorUserId;
            boolean targetIsSuperAdmin = targetUserId != null && targetUserId == 1L;
            boolean callerIsSelf = callerId != null && callerId.equals(changePasswordRequest.getUserId());
            if (targetIsSuperAdmin && !callerIsSelf) {
                SecurityAuditLogger.logBlocked(LOGGER, httpServletRequest, "bola_privilege_escalation", "change_super_admin_password", String.valueOf(changePasswordRequest.getUserId()));
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden");
            }

            String encodedNewPassword = encoder.encode(changePasswordRequest.getNewPassword());
            // Update password
            Boolean result = userMapper.updatePassword(changePasswordRequest.getUserId(), encodedNewPassword);

            if (result) {
                // BOPLA guard: role assignment is intentionally NOT performed during password change.
                // Role updates must go through the dedicated role-management endpoint to maintain audit trail
                // and prevent privilege escalation by combining two sensitive operations in one request.

                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.CHANGE_PASSWORD_PATH, null, null, "User", "User (change-password)", "change-password", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Password updated successfully.", true));
            } else {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Password update failed.", false));
            }

        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.CHANGE_PASSWORD_PATH, errorLine, error.toString(), "User", "User (Change Password)", "Change Password", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("Internal server error.", null, false));
        }
    }

    /** Service implementation method. */
    public User getUserAuth() {
        // Query flow: load user auth and return API response.
        try {
            // OAuth2/JWT principal path (preferred for stateless APIs).
            var currentUser = com.ut.emrPacs.authentication.session.UserAuthSession.getCurrentUser();
            if (currentUser != null && currentUser.userId() != null) {
                User user = new User();
                user.setId(currentUser.userId());
                user.setUsername(currentUser.username());
                return user;
            }

            // Legacy SecurityContext username path (fallback for existing flows).
            if (UserAuthHelper.getUserAuth() != null && UserAuthHelper.getUserAuth().getName() != null) {
                String identifier = UserAuthHelper.getUserAuth().getName().trim();
                if (!identifier.isEmpty()) {
                    List<User> userList = userMapper.getOneByUsername(identifier);
                    if (userList != null && !userList.isEmpty()) {
                        return userList.get(0);
                    }
                }
            } else {
                return new User();
            }
            return new User();
        } catch (Exception error) {
            return new User();
        }
    }

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> updateUser(UserUpdateRequest userUpdateRequest, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Update flow: validate request, apply changes to user, and return operation result.
        LocalTime startDuration = LocalTime.now();
        try {
            // BOPLA/BOLA guard: reject requests with missing target user identity.
            if (userUpdateRequest == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            userUpdateRequest.setId(publicEntityKeyResolver.resolve(Entity.USER, userUpdateRequest.getPublicKey(), userUpdateRequest.getId()));
            if (userUpdateRequest.getId() == null || userUpdateRequest.getId() <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }

            // BOPLA guard: verify target user actually exists before applying changes
            List<UserResponse> targetUserList = userMapper.getOneByUserId(userUpdateRequest.getId());
            if (targetUserList == null || targetUserList.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("User not found.", false));
            }

            Long actorUserId = currentUserId();
            boolean targetIsSuperAdmin = SUPER_ADMIN_USER_ID.equals(userUpdateRequest.getId());
            Long actorHospitalId = currentHospitalId();
            boolean canViewAllHospitals = canAccessAllUserHospitals(actorUserId);
            if (!canViewAllHospitals && !isUserInHospital(userUpdateRequest.getId(), actorHospitalId)) {
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden");
            }

            applyPublicKeyRelations(userUpdateRequest);
            List<Long> targetHospitalIds = normalizeIds(userUpdateRequest.getHospitalIds(), null);
            boolean hasHospitalSelection = userUpdateRequest.getHospitalIds() != null;
            if (targetIsSuperAdmin && hasHospitalSelection) {
                targetHospitalIds = allActiveHospitalIds();
            }
            if (hasHospitalSelection && targetHospitalIds.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("At least one hospital is required.", false));
            }
            if (hasHospitalSelection && !canViewAllHospitals && !containsOnlyHospital(targetHospitalIds, actorHospitalId)) {
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden");
            }

            String username = userUpdateRequest.getUsername() != null ? userUpdateRequest.getUsername().trim() : null;
            if (username != null) {
                if (username.isEmpty()) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Username is required.", false));
                }
                if (userMapper.checkDuplicateForUpdate(username, userUpdateRequest.getId()) > 0) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Username", false));
                }
                userUpdateRequest.setUsername(username);
            }

            if (userUpdateRequest.getIsActive() != null
                    && userUpdateRequest.getIsActive() != 0
                    && userUpdateRequest.getIsActive() != 1) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid active status.", false));
            }
            if (targetIsSuperAdmin && userUpdateRequest.getIsActive() != null && userUpdateRequest.getIsActive() != 1) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Super admin cannot be deactivated.", false));
            }

            String password = userUpdateRequest.getPassword() != null ? userUpdateRequest.getPassword() : "";
            if (!password.isBlank()) {
                Long callerId = actorUserId;
                Long targetUserId = userUpdateRequest.getId();
                boolean callerIsSelf = callerId != null && callerId.equals(targetUserId);
                if (targetIsSuperAdmin && !callerIsSelf) {
                    SecurityAuditLogger.logBlocked(LOGGER, httpServletRequest, "bola_privilege_escalation", "update_super_admin_password", String.valueOf(targetUserId));
                    return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden");
                }
                String policyError = PasswordPolicy.validatePasswordChange(password);
                if (policyError != null) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message(policyError, false));
                }
            }

            List<Long> targetUserGroupIds = normalizeIds(userUpdateRequest.getUserGroupIds(), null);
            boolean hasUserGroupSelection = userUpdateRequest.getUserGroupIds() != null;
            if (targetIsSuperAdmin && hasUserGroupSelection) {
                targetUserGroupIds = allActiveRoleIds();
            }
            if (hasUserGroupSelection && !isSuperAdmin(actorUserId) && containsProtectedScopeRole(targetUserGroupIds)) {
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden");
            }

            // Update user
            userUpdateRequest.setModifiedBy(actorUserId);
            Boolean result = userMapper.updateUser(userUpdateRequest);
            if (result) {
                if (hasHospitalSelection) {
                    replaceUserHospitals(targetHospitalIds, userUpdateRequest.getId());
                }
                if (hasUserGroupSelection) {
                    replaceUserGroups(targetUserGroupIds, userUpdateRequest.getId());
                }
                if (!password.isBlank()) {
                    userMapper.updatePassword(userUpdateRequest.getId(), encoder.encode(password));
                }

                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.UPDATE_PATH,null,null,"User","User (update)","update",1,"Success",startDuration,endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));

            } else {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
            }

        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.UPDATE_PATH, errorLine, error.toString(),"User","User (update)","Update",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }


    private void replaceUserHospitals(List<Long> hospitalIds, Long userId){
        // Insert flow: validate request, create user hospital, and return operation result.
        if (hospitalIds == null || hospitalIds.isEmpty() || userId == null || userId <= 0) {
            return;
        }
        if (isSuperAdmin(userId)) {
            syncSuperAdminHospitals();
            return;
        }
        userMapper.deleteUserHospital(userId);
        for (int index = 0; index < hospitalIds.size(); index++) {
            Long hospitalId = hospitalIds.get(index);
            if (hospitalId == null || hospitalId <= 0) {
                continue;
            }
            userMapper.insertUserHospital(hospitalId, userId, index == 0);
        }
    }

    private void replaceUserGroups(List<Long> userGroupIds, Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        if (isSuperAdmin(userId)) {
            syncSuperAdminRoleMemberships();
            return;
        }

        roleMapper.deleteRoleUser(userId);
        if (userGroupIds != null) {
            for (Long groupId : userGroupIds) {
                if (groupId == null || groupId <= 0) {
                    continue;
                }
                roleMapper.insertRoleUser(groupId, userId);
            }
        }
        rolePermissionMapper.bumpPermissionVersion(userId);
        permissionCacheService.invalidateByUser(userId);
    }

    private void syncSuperAdminHospitals() {
        List<Long> hospitalIds = allActiveHospitalIds();
        if (hospitalIds.isEmpty()) {
            return;
        }
        userMapper.deleteUserHospital(SUPER_ADMIN_USER_ID);
        for (int index = 0; index < hospitalIds.size(); index++) {
            userMapper.insertUserHospital(hospitalIds.get(index), SUPER_ADMIN_USER_ID, index == 0);
        }
    }

    private void syncSuperAdminRoleMemberships() {
        List<Long> roleIds = allActiveRoleIds();
        if (roleIds.isEmpty()) {
            return;
        }
        roleMapper.deleteRoleUser(SUPER_ADMIN_USER_ID);
        for (Long roleId : roleIds) {
            roleMapper.insertRoleUser(roleId, SUPER_ADMIN_USER_ID);
        }
        rolePermissionMapper.bumpPermissionVersion(SUPER_ADMIN_USER_ID);
        permissionCacheService.invalidateByUser(SUPER_ADMIN_USER_ID);
    }

    private List<Long> allActiveHospitalIds() {
        List<Long> hospitalIds = userMapper.findAllActiveHospitalIds();
        return hospitalIds == null ? List.of() : hospitalIds;
    }

    private List<Long> allActiveRoleIds() {
        List<Long> roleIds = roleMapper.listActiveRoleIds();
        return roleIds == null ? List.of() : roleIds;
    }

    private static List<Long> normalizeIds(List<Long> ids, Long fallbackId) {
        Set<Long> normalized = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream()
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .forEach(normalized::add);
        } else if (fallbackId != null && fallbackId > 0) {
            normalized.add(fallbackId);
        }
        return new ArrayList<>(normalized);
    }

    private void applyPublicKeyRelations(UserUpdateRequest request) {
        if (request == null) {
            return;
        }
        List<Long> hospitalIds = resolveKeyList(Entity.HOSPITAL, request.getHospitalKeys(), "Hospital");
        if (hospitalIds != null) {
            request.setHospitalIds(hospitalIds);
        }
        List<Long> groupIds = resolveKeyList(Entity.ROLE, request.getUserGroupKeys(), "User group");
        if (groupIds != null) {
            request.setUserGroupIds(groupIds);
        }
    }

    private void applyPublicKeyRelations(UserRequest request) {
        if (request == null) {
            return;
        }
        List<Long> hospitalIds = resolveKeyList(Entity.HOSPITAL, request.getHospitalKeys(), "Hospital");
        if (hospitalIds != null) {
            request.setHospitalIds(hospitalIds);
        }
        List<Long> groupIds = resolveKeyList(Entity.ROLE, request.getUserGroupKeys(), "User group");
        if (groupIds != null) {
            request.setUserGroupIds(groupIds);
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

    private static boolean containsOnlyHospital(List<Long> hospitalIds, Long hospitalId) {
        if (hospitalIds == null || hospitalIds.isEmpty() || hospitalId == null || hospitalId <= 0) {
            return false;
        }
        return hospitalIds.stream().allMatch(id -> hospitalId.equals(id));
    }

    private boolean containsProtectedScopeRole(List<Long> userGroupIds) {
        if (userGroupIds == null || userGroupIds.isEmpty()) {
            return false;
        }
        for (Long groupId : userGroupIds) {
            if (groupId == null || groupId <= 0) {
                continue;
            }
            Long count = roleMapper.countRoleByIdAndCode(groupId, CROSS_HOSPITAL_USER_SCOPE_ROLE_CODE);
            if (count != null && count > 0) {
                return true;
            }
        }
        return false;
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

    private boolean isProtectedPasswordResetUser(UserResponse user) {
        if (user == null) {
            return false;
        }
        if (SUPER_ADMIN_USER_ID.equals(user.getId())) {
            return true;
        }
        if (Long.valueOf(9L).equals(user.getUserType())) {
            return true;
        }
        String username = user.getUsername() == null ? "" : user.getUsername().trim().toLowerCase();
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim().toLowerCase();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim().toLowerCase();
        String displayName = (firstName + " " + lastName).trim();
        return "superadmin".equals(username)
                || "super admin".equals(displayName)
                || "useradmin".equals(username)
                || "user admin".equals(displayName);
    }

    private boolean isSuperAdmin(Long userId) {
        return SUPER_ADMIN_USER_ID.equals(userId);
    }

    private boolean isUserInHospital(Long targetUserId, Long hospitalId) {
        if (targetUserId == null || hospitalId == null || hospitalId <= 0) {
            return false;
        }
        Long count = userMapper.countActiveUserInHospital(targetUserId, hospitalId);
        return count != null && count > 0;
    }


    /** {@inheritDoc} */
    @Override
    public ResponseMessage<String> deleteUser(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Delete flow: validate request, remove or deactivate user, and return operation result.
        LocalTime startDuration = LocalTime.now();
        try {
            
            if (SUPER_ADMIN_USER_ID.equals(id)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Super admin cannot be deleted.", false));
            }

            Long actorUserId = currentUserId();
            Long actorHospitalId = currentHospitalId();
            boolean canViewAllHospitals = canAccessAllUserHospitals(actorUserId);
            if (!canViewAllHospitals && !isUserInHospital(id, actorHospitalId)) {
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden");
            }

            // Delete user
            Boolean result = userMapper.deleteUser(id, actorUserId);
            if (result) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.DELETE_PATH,null,null,"User","User (Delete)","Delete",1,"Success",startDuration,endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            } else {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
            }
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.User.BASE_PATH + ApiConstants.User.DELETE_PATH, errorLine, error.toString(),"User","User (Delete)","Delete",2,"Error",startDuration,endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private static Long currentHospitalId() {
        var principal = com.ut.emrPacs.authentication.session.UserAuthSession.getCurrentUser();
        if (principal == null || principal.hospitalId() == null) {
            throw new IllegalStateException("Hospital context not found in OAuth2 token claims.");
        }
        return principal.hospitalId();
    }

    private static Long currentUserId() {
        var principal = com.ut.emrPacs.authentication.session.UserAuthSession.getCurrentUser();
        if (principal == null) {
            throw new IllegalStateException("User context not found in OAuth2 token claims.");
        }
        return principal.userId();
    }

    private static String formatGroupDate(String value) {
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

}
