package com.ut.emrPacs.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiConstantsCoverageTest {

    @Test
    void endpointPathsShouldBeUniqueAcrossApiConstants() throws IllegalAccessException {
        Map<String, String> seen = new HashMap<>();

        for (Class<?> nested : ApiConstants.class.getDeclaredClasses()) {
            String basePath = null;
            for (Field field : nested.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                    continue;
                }
                field.setAccessible(true);
                if ("BASE_PATH".equals(field.getName())) {
                    basePath = (String) field.get(null);
                }
            }

            for (Field field : nested.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                    continue;
                }
                String fieldName = field.getName();
                if (!fieldName.endsWith("_PATH") || "BASE_PATH".equals(fieldName) || fieldName.endsWith("FULL_PATH")) {
                    continue;
                }

                field.setAccessible(true);
                String path = (String) field.get(null);
                if (path == null || path.isBlank()) {
                    continue;
                }

                String fullPath = (basePath != null && !path.startsWith("/report/")) ? basePath + path : path;
                String owner = nested.getSimpleName() + "." + fieldName;

                String existing = seen.putIfAbsent(fullPath, owner);
                assertTrue(existing == null, () -> "Duplicate endpoint path detected: " + fullPath + " in " + owner + " and " + existing);
            }
        }
    }

    @Test
    void authEndpointsShouldStayUnderAuthBase() throws IllegalAccessException {
        for (Field field : ApiConstants.Auth.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            String fieldName = field.getName();
            if (!fieldName.endsWith("_PATH") || "BASE_PATH".equals(fieldName) || fieldName.endsWith("FULL_PATH")) {
                continue;
            }
            field.setAccessible(true);
            String path = (String) field.get(null);
            assertTrue(path != null && path.startsWith("/auth-"), () -> "Auth endpoint must follow /auth-* naming: " + fieldName + "=" + path);
        }

        assertFalse(ApiConstants.User.ME_PATH.startsWith("/auth"), "User ME endpoint must stay under UserController path.");
    }
}
