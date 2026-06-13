package com.ut.emrPacs.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserManagementFlowRequestContractTest {

    @Test
    void moduleTypeListShouldUseRequestBodyContract() throws Exception {
        Method method = ModuleTypeController.class.getDeclaredMethod("listModuleType", com.ut.emrPacs.model.base.filter.ModuleTypeFilter.class, jakarta.servlet.http.HttpServletRequest.class);
        assertHasRequestBody(method, 0);
        assertNoRequestParam(method, 0);
    }

    @Test
    void saveRolePermissionsShouldUseSingleRequestBodyContract() throws Exception {
        Method method = PermissionController.class.getDeclaredMethod("save", com.ut.emrPacs.model.dto.request.authentication.permission.SaveRolePermissionsRequest.class, jakarta.servlet.http.HttpServletRequest.class);
        assertHasRequestBody(method, 0);
        assertNoRequestParam(method, 0);
    }

    @Test
    void roleCreateAndUpdateShouldNotUseBindingResultParameter() {
        Method[] methods = RoleController.class.getDeclaredMethods();
        for (Method method : methods) {
            if (!"add".equals(method.getName()) && !"updateRole".equals(method.getName())) {
                continue;
            }
            for (Parameter parameter : method.getParameters()) {
                assertTrue(!"BindingResult".equals(parameter.getType().getSimpleName()),
                        () -> method.getName() + " should not expose BindingResult in controller contract");
            }
        }
    }

    @Test
    void roleCreateShouldUseRequestDtoFromRequestPackage() throws Exception {
        Method method = RoleController.class.getDeclaredMethod("add", com.ut.emrPacs.model.dto.request.authentication.role.RoleCreateRequest.class, jakarta.servlet.http.HttpServletRequest.class);
        assertHasRequestBody(method, 0);
    }

    @Test
    void roleWriteRequestsShouldNotExposeLegacyRelationFields() {
        assertNoField(com.ut.emrPacs.model.dto.request.authentication.role.RoleCreateRequest.class, "userList");
        assertNoField(com.ut.emrPacs.model.dto.request.authentication.role.RoleCreateRequest.class, "moduleList");
        assertNoField(com.ut.emrPacs.model.dto.request.authentication.role.RoleDataUpdate.class, "userList");
        assertNoField(com.ut.emrPacs.model.dto.request.authentication.role.RoleDataUpdate.class, "moduleList");
    }

    @Test
    void userUpdateRequestShouldEnforceCriticalValidation() throws Exception {
        Class<?> type = com.ut.emrPacs.model.dto.request.authentication.user.UserUpdateRequest.class;
        assertHasField(type, "publicKey");
        assertHasAnnotation(type, "email", "Email");
    }

    @Test
    void userRequestShouldEnforceCriticalValidation() throws Exception {
        Class<?> type = com.ut.emrPacs.model.users.UserRequest.class;
        assertHasAnnotation(type, "username", "NotBlank");
        assertHasAnnotation(type, "password", "NotBlank");
        assertHasAnnotation(type, "email", "Email");
    }

    @Test
    void changePasswordRequestShouldEnforceCriticalValidation() throws Exception {
        Class<?> type = com.ut.emrPacs.model.dto.request.authentication.changePassword.ChangePasswordRequest.class;
        assertHasAnnotation(type, "userId", "JsonIgnore");
        assertHasAnnotation(type, "userKey", "NotBlank");
        assertHasAnnotation(type, "newPassword", "NotBlank");
        assertHasAnnotation(type, "confirmPassword", "NotBlank");
    }

    private static void assertHasRequestBody(Method method, int index) {
        Parameter parameter = method.getParameters()[index];
        assertTrue(parameter.isAnnotationPresent(RequestBody.class),
                () -> method.getName() + " parameter " + index + " must use @RequestBody");
    }

    private static void assertNoRequestParam(Method method, int index) {
        Parameter parameter = method.getParameters()[index];
        assertTrue(!parameter.isAnnotationPresent(RequestParam.class),
                () -> method.getName() + " parameter " + index + " must not use @RequestParam");
    }

    private static void assertNoField(Class<?> type, String fieldName) {
        boolean exists = Arrays.stream(type.getDeclaredFields()).anyMatch(field -> fieldName.equals(field.getName()));
        assertTrue(!exists, () -> type.getSimpleName() + " must not declare legacy field: " + fieldName);
    }

    private static void assertHasField(Class<?> type, String fieldName) {
        boolean exists = Arrays.stream(type.getDeclaredFields()).anyMatch(field -> fieldName.equals(field.getName()));
        assertTrue(exists, () -> type.getSimpleName() + " must declare field: " + fieldName);
    }

    private static void assertHasAnnotation(Class<?> type, String fieldName, String annotationSimpleName) throws Exception {
        boolean present = Arrays.stream(type.getDeclaredField(fieldName).getAnnotations())
                .anyMatch(annotation -> annotation.annotationType().getSimpleName().equals(annotationSimpleName));
        assertTrue(present, () -> type.getSimpleName() + "." + fieldName + " must have @" + annotationSimpleName);
    }
}
