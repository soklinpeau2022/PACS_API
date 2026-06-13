package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.authentication.permission.SaveRolePermissionsRequest;
import com.ut.emrPacs.service.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping(ApiConstants.Permission.BASE_PATH)
@Tag(name = "8. Permission Controller", description = "Endpoints for permission and role-permission management.")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.Permission.TREE_PATH)
    @Operation(summary = "Get permission tree", description = "Module -> Permission. Endpoint -> POST /permission/permission-tree. Returns module/action tree with checked=true/false for frontend menu/button visibility.")
    public ResponseMessage<BaseResult> tree(HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        var principal = UserAuthSession.getCurrentUser();
        Long userId = principal != null ? principal.userId() : null;
        Long hospitalId = principal != null ? principal.hospitalId() : null;
        Long permissionVersion = principal != null ? principal.permissionVersion() : null;
        return permissionService.permissionTree(userId, hospitalId, permissionVersion, httpServletRequest);
    }

    @PostMapping(ApiConstants.Permission.SAVE_ROLE_PERMISSIONS_PATH)
    @Operation(summary = "Save role permissions", description = "Module -> Permission. Endpoint -> POST /permission/permission-save-role-permissions. Replaces role permissions using validated moduleDetailIds.")
    public ResponseMessage<BaseResult> save(@Valid @RequestBody SaveRolePermissionsRequest request,
                                            HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        var principal = UserAuthSession.getCurrentUser();
        Long actor = principal != null ? principal.userId() : null;
        Long roleId = publicEntityKeyResolver.resolveRequired(Entity.ROLE, request.getRoleKey(), null, "Role");
        Long[] moduleDetailIds = resolveModuleDetailKeys(request.getModuleDetailKeys());
        return permissionService.saveRolePermissions(roleId, moduleDetailIds, actor, httpServletRequest);
    }

    private Long[] resolveModuleDetailKeys(List<String> moduleDetailKeys) {
        if (moduleDetailKeys == null || moduleDetailKeys.isEmpty()) {
            return new Long[0];
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String key : moduleDetailKeys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            ids.add(publicEntityKeyResolver.resolveRequired(Entity.MODULE_DETAIL, key.trim(), null, "Module detail"));
        }
        return new ArrayList<>(ids).toArray(new Long[0]);
    }
}

