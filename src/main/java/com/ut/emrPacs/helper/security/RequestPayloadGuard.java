package com.ut.emrPacs.helper.security;

import com.ut.emrPacs.helper.sql.SqlSanitizerHelper;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Defensive validation for request payloads to reduce injection risks.
 *
 * <p>Applied globally by {@code RequestBodySanitizerAdvice} so it runs for all request DTOs.</p>
 */
public final class RequestPayloadGuard {

    private static final Pattern ID_LIST_PATTERN = Pattern.compile("^\\d+(\\s*,\\s*\\d+)*$");
    private static final Pattern ID_LIST_ITEM_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]+$");

    private static final Set<String> TABLE_FIELDS = Set.of(
            "table",
            "tablename",
            "tablenamedetail",
            "ledgertablename"
    );

    private static final Set<String> IDENTIFIER_FIELDS = Set.of(
            "field",
            "fieldfree",
            "modulecolumn",
            "descriptioncolumn",
            "typenamecol",
            "periodkeycol"
    );

    private static final Set<String> ORDER_BY_FIELDS = Set.of(
            "orderby",
            "sort",
            "sortby"
    );

    private RequestPayloadGuard() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void validate(Object payload) {
        if (payload == null) {
            return;
        }
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        validateObject(payload, null, visited);
    }

    private static void validateObject(Object value, String fieldName, IdentityHashMap<Object, Boolean> visited) {
        if (value == null) {
            return;
        }

        if (value instanceof String stringValue) {
            validateString(fieldName, stringValue);
            return;
        }

        if (value instanceof Number numberValue) {
            validateNumber(fieldName, numberValue);
            return;
        }

        if (value instanceof Map<?, ?> map) {
            if (visited.put(value, Boolean.TRUE) != null) {
                return;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? null : String.valueOf(entry.getKey());
                validateObject(entry.getValue(), key, visited);
            }
            return;
        }

        if (value instanceof Collection<?> collection) {
            if (visited.put(value, Boolean.TRUE) != null) {
                return;
            }
            for (Object item : collection) {
                validateCollectionItem(item, fieldName, visited);
            }
            return;
        }

        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            if (visited.put(value, Boolean.TRUE) != null) {
                return;
            }
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                validateCollectionItem(Array.get(value, index), fieldName, visited);
            }
            return;
        }

        if (isSimpleType(valueClass)) {
            return;
        }

        if (visited.put(value, Boolean.TRUE) != null) {
            return;
        }

        for (Class<?> type = valueClass; type != null && type != Object.class; type = type.getSuperclass()) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object fieldValue;
                try {
                    fieldValue = field.get(value);
                } catch (IllegalAccessException ignored) {
                    continue;
                }
                validateObject(fieldValue, field.getName(), visited);
            }
        }
    }

    private static void validateCollectionItem(Object value, String fieldName, IdentityHashMap<Object, Boolean> visited) {
        if (value instanceof String stringValue && isIdListKey(normalizeKey(fieldName))) {
            validateIdListItem(fieldName, stringValue);
            return;
        }
        validateObject(value, fieldName, visited);
    }

    private static void validateString(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (UnicodeGuard.containsDisallowed(value)) {
            throw new IllegalArgumentException("Invalid request parameters.");
        }

        String key = normalizeKey(fieldName);
        if (key.isEmpty()) {
            return;
        }

        if (TABLE_FIELDS.contains(key)) {
            SqlSanitizerHelper.requireSafeTableName(value);
            return;
        }

        if (IDENTIFIER_FIELDS.contains(key)) {
            SqlSanitizerHelper.requireSafeDottedIdentifier(value);
            return;
        }

        if (ORDER_BY_FIELDS.contains(key)) {
            if (SqlSanitizerHelper.sanitizeOrderBy(value) == null) {
                throw new IllegalArgumentException("Invalid request parameters.");
            }
            return;
        }

        if (isIdListKey(key)) {
            if (!ID_LIST_PATTERN.matcher(value.trim()).matches()) {
                throw new IllegalArgumentException("Invalid request parameters.");
            }
        }
    }

    private static void validateIdListItem(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (UnicodeGuard.containsDisallowed(value)) {
            throw new IllegalArgumentException("Invalid request parameters.");
        }
        if (!ID_LIST_ITEM_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("Invalid request parameters.");
        }
    }

    private static void validateNumber(String fieldName, Number value) {
        if (value == null) {
            return;
        }
        String key = normalizeKey(fieldName);
        if (key.isEmpty()) {
            return;
        }

        if (isIdKey(key) || isPaginationKey(key)) {
            if (value.longValue() < 0) {
                throw new IllegalArgumentException("Invalid request parameters.");
            }
        }
    }

    private static boolean isPaginationKey(String key) {
        return "page".equals(key) || "rowsperpage".equals(key);
    }

    private static boolean isIdKey(String key) {
        return key.equals("id") || key.endsWith("id");
    }

    private static boolean isIdListKey(String key) {
        return key.endsWith("idlist") || key.endsWith("ids");
    }

    private static String normalizeKey(String fieldName) {
        if (fieldName == null) {
            return "";
        }
        String trimmed = fieldName.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        return lowered.replace("_", "");
    }

    private static boolean isSimpleType(Class<?> type) {
        if (type.isPrimitive()) {
            return true;
        }
        if (type.isEnum()) {
            return true;
        }
        if (Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type) || type.isArray()) {
            return false;
        }
        if (type == String.class || Number.class.isAssignableFrom(type) || Boolean.class == type || Character.class == type) {
            return true;
        }
        if (Date.class.isAssignableFrom(type) || type.getName().startsWith("java.time.")) {
            return true;
        }
        String pkg = type.getPackageName();
        return pkg.startsWith("java.") || pkg.startsWith("javax.") || pkg.startsWith("jakarta.");
    }
}
