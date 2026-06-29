package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.RoleListFilter;
import com.ut.emrPacs.model.dto.request.authentication.role.RoleCreateRequest;
import com.ut.emrPacs.model.dto.request.authentication.role.RoleDataUpdate;
import com.ut.emrPacs.service.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.UnknownHostException;

@RestController
@Validated
@RequestMapping(ApiConstants.Role.BASE_PATH)
@Tag(name = "76. Role Controller", description = "Endpoints for role management.")
public class RoleController {

    @Autowired
    private RoleService roleService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.Role.LIST_PATH)
    @Operation(summary = "List roles", description = "Module -> Role. Endpoint -> POST /role/role-list")
    public ResponseMessage<BaseResult> listRole(@Valid @RequestBody RoleListFilter filter,
                                                HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return roleService.listRole(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Role.USER_GROUP_LIST_PATH)
    @Operation(summary = "List roles with users", description = "Module -> Role. Endpoint -> POST /role/user-group-list")
    public ResponseMessage<BaseResult> listRoleUserGroupl(@Valid @RequestBody RoleListFilter filter,
                                                          HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return roleService.listRoleUserGroupl(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Role.USER_GROUP_SUMMARY_PATH)
    @Operation(summary = "Summarize user groups", description = "Module -> Role. Endpoint -> POST /role/user-group-summary")
    public ResponseMessage<BaseResult> summarizeRoleUserGroups(@Valid @RequestBody(required = false) RoleListFilter filter,
                                                               HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return roleService.summarizeRoleUserGroups(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Role.FIND_PATH)
    @Operation(summary = "Find role by ID", description = "Module -> Role. Endpoint -> POST /role/role-find/{id}")
    public ResponseMessage<BaseResult> getRoleById(@PathVariable String id,
                                                   HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return roleService.getRoleById(publicEntityKeyResolver.resolveFromPath(Entity.ROLE, id, "Role"), httpServletRequest);
    }

    @PostMapping(value = ApiConstants.Role.CREATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create role", description = "Module -> Role. Endpoint -> POST /role/role-add")
    public ResponseMessage<BaseResult> add(@Valid @RequestBody RoleCreateRequest roleData,
                                           HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return roleService.createRole(roleData, httpServletRequest);
    }

    @PostMapping(value = ApiConstants.Role.UPDATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update role", description = "Module -> Role. Endpoint -> POST /role/role-update")
    public ResponseMessage<BaseResult> updateRole(@Valid @RequestBody RoleDataUpdate roleDataUpdate,
                                                  HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return roleService.updateRole(roleDataUpdate, httpServletRequest);
    }

    @PostMapping(ApiConstants.Role.MENU_PATH)
    @Operation(summary = "Get role menu", description = "Module -> Role. Endpoint -> POST /role/role-menu")
    public ResponseMessage<BaseResult> menu(HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return roleService.menu(httpServletRequest);
    }

    @PostMapping(ApiConstants.Role.DELETE_PATH)
    @Operation(summary = "Delete role by ID", description = "Module -> Role. Endpoint -> POST /role/role-delete/{id}")
    public ResponseMessage<BaseResult> deleteRole(@PathVariable String id,
                                                  HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return roleService.deleteRole(publicEntityKeyResolver.resolveFromPath(Entity.ROLE, id, "Role"), httpServletRequest);
    }
}
