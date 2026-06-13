package com.ut.emrPacs.helper.security;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SecurityPayloadSanitizerHelper {

    private static final int MAX_DEPTH = 16;

    private SecurityPayloadSanitizerHelper() {
    }

    public static <T> T sanitizeInPlace(T root) {
        sanitizeValue(root, new IdentityHashMap<>(), 0);
        return root;
    }

    @SuppressWarnings("unchecked")
    private static Object sanitizeValue(Object value, IdentityHashMap<Object, Boolean> visited, int depth) {
        if (value == null) {
            return null;
        }
        if (depth > MAX_DEPTH) {
            return value;
        }
        if (value instanceof String stringValue) {
            if (isHttpUrl(stringValue)) {
                return stripControlCharacters(stringValue);
            }
            return SecurityInputSanitizerHelper.sanitize(stringValue);
        }

        Class<?> type = value.getClass();
        if (type.isArray()) {
            if (visited.put(value, Boolean.TRUE) != null) {
                return value;
            }
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object item = Array.get(value, i);
                Object sanitizedItem = sanitizeValue(item, visited, depth + 1);
                if (sanitizedItem != item) {
                    Array.set(value, i, sanitizedItem);
                }
            }
            return value;
        }

        switch (value) {
            case List<?> list -> {
                if (visited.put(value, Boolean.TRUE) != null) {
                    return value;
                }
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    Object sanitizedItem = sanitizeValue(item, visited, depth + 1);
                    if (sanitizedItem != item) {
                        try {
                            ((List<Object>) list).set(i, sanitizedItem);
                        } catch (UnsupportedOperationException ignored) {
                            // immutable list
                        }
                    }
                }
                return value;
            }
            case Collection<?> collection -> {
                if (visited.put(value, Boolean.TRUE) != null) {
                    return value;
                }
                List<Object> sanitizedItems = new ArrayList<>(collection.size());
                for (Object item : collection) {
                    sanitizedItems.add(sanitizeValue(item, visited, depth + 1));
                }
                try {
                    Collection<Object> mutableCollection = (Collection<Object>) collection;
                    mutableCollection.clear();
                    mutableCollection.addAll(sanitizedItems);
                } catch (UnsupportedOperationException ignored) {
                    // immutable collection
                }
                return value;
            }
            case Map<?, ?> map -> {
                if (visited.put(value, Boolean.TRUE) != null) {
                    return value;
                }
                List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
                for (Map.Entry<?, ?> entry : entries) {
                    Object key = entry.getKey();
                    Object sanitizedValue = sanitizeValue(entry.getValue(), visited, depth + 1);
                    if (sanitizedValue != entry.getValue()) {
                        try {
                            ((Map<Object, Object>) map).put(key, sanitizedValue);
                        } catch (UnsupportedOperationException ignored) {
                            // immutable map
                        }
                    }
                }
                return value;
            }
            default -> {
            }
        }

        if (isSimpleType(type)) {
            return value;
        }
        if (visited.put(value, Boolean.TRUE) != null) {
            return value;
        }

        sanitizeFields(value, visited, depth + 1);
        return value;
    }

    private static void sanitizeFields(Object target, IdentityHashMap<Object, Boolean> visited, int depth) {
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers)) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                } catch (RuntimeException ignored) {
                    continue;
                }

                if (isTrustedPacsResultRichTextField(target, field)) {
                    continue;
                }

                Object fieldValue;
                try {
                    fieldValue = field.get(target);
                } catch (IllegalAccessException ignored) {
                    continue;
                }

                Object sanitizedValue = sanitizeValue(fieldValue, visited, depth);
                if (sanitizedValue != fieldValue && !Modifier.isFinal(modifiers)) {
                    try {
                        field.set(target, sanitizedValue);
                    } catch (IllegalAccessException ignored) {
                        // ignore inaccessible field assignment
                    }
                }
            }
        }
    }

    private static boolean isSimpleType(Class<?> type) {
        if (type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || type == Boolean.class
                || type == Character.class
                || type == String.class
                || type == UUID.class
                || type == Date.class
                || type == BigDecimal.class
                || type == BigInteger.class
                || Temporal.class.isAssignableFrom(type)
                || type == Class.class
                || type.isEnum()) {
            return true;
        }
        String name = type.getName();
        if (name.startsWith("java.") || name.startsWith("javax.")) {
            return true;
        }
        return name.startsWith("java.time.")
                || name.startsWith("java.net.")
                || name.startsWith("java.nio.");
    }

    private static boolean isTrustedPacsResultRichTextField(Object target, Field field) {
        String typeName = target.getClass().getName();
        if (!typeName.startsWith("com.ut.emrPacs.model.dto.response.pacs.result.")
                && !typeName.startsWith("com.ut.emrPacs.model.dto.request.pacs.result.")) {
            return false;
        }
        String fieldName = field.getName();
        return "resultText".equals(fieldName) || "templateContent".equals(fieldName);
    }

    private static boolean isHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    private static String stripControlCharacters(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!Character.isISOControl(current)) {
                builder.append(current);
            }
        }
        return builder.toString().trim();
    }
}
