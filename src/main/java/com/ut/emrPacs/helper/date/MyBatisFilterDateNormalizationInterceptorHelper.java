package com.ut.emrPacs.helper.date;

import com.ut.emrPacs.model.base.filter.FilterBase;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * MyBatis interceptor that normalizes date-like filter fields before query execution.
 *
 * <p>Targets:</p>
 * <ul>
 *   <li>Any parameter object implementing {@link FilterBase}</li>
 *   <li>Any map that contains a {@link FilterBase} value</li>
 *   <li>Any parameter object whose class name ends with {@code Filter}</li>
 * </ul>
 *
 * <p>Normalization rules are delegated to {@link FormatDateHelper}.</p>
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class MyBatisFilterDateNormalizationInterceptorHelper implements Interceptor {

    /**
     * Intercepts MyBatis calls and normalizes parameter object dates before execution.
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        if (args != null && args.length > 1) {
            normalizeParameterObject(args[1]);
        }
        return invocation.proceed();
    }

    /**
     * Wraps target using MyBatis {@link Plugin}.
     */
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /**
     * No configuration properties are currently used.
     */
    @Override
    public void setProperties(Properties properties) {
        // no-op
    }

    /**
     * Detects supported parameter object types and applies normalization.
     */
    private static void normalizeParameterObject(Object parameterObject) {
        switch (parameterObject) {
            case null -> {
                return;
            }
            case FilterBase filterBase -> {
                normalizeFilterObject(parameterObject);
                return;
            }
            case Map<?, ?> map -> {
                normalizeMap(map);
                return;
            }
            default -> {
            }
        }

        String simpleName = parameterObject.getClass().getSimpleName();
        if (simpleName.endsWith("Filter")) {
            normalizeFilterObject(parameterObject);
        }
    }

    /**
     * Walks a parameter map (including nested maps) and normalizes date-like string values.
     */
    private static void normalizeMap(Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object keyObj = entry.getKey();
            Object value = entry.getValue();

            switch (value) {
                case null -> {
                    continue;
                }
                case FilterBase filterBase -> {
                    normalizeFilterObject(value);
                    continue;
                }
                case Map<?, ?> nested -> {
                    normalizeMap(nested);
                    continue;
                }
                default -> {
                }
            }

            if (!(keyObj instanceof String key)) continue;
            if (!(value instanceof String stringValue)) continue;

            if (!isDateKey(key)) continue;

            String normalized = normalizeDateValue(key, stringValue);
            if (!Objects.equals(stringValue, normalized)) {
                try {
                    putMapValue(map, keyObj, normalized);
                } catch (RuntimeException ignored) {
                    // If map is unmodifiable, ignore.
                }
            }
        }
    }

    /**
     * Normalizes string fields on filter objects where the field name looks date-like.
     */
    private static void normalizeFilterObject(Object filterObject) {
        for (Class<?> clazz = filterObject.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.getType() != String.class) continue;
                if (Modifier.isStatic(field.getModifiers())) continue;

                String fieldName = field.getName();
                if (!isDateFieldName(fieldName)) continue;

                field.setAccessible(true);
                try {
                    String current = (String) field.get(filterObject);
                    String normalized = normalizeDateValue(fieldName, current);
                    if (!Objects.equals(current, normalized)) {
                        field.set(filterObject, normalized);
                    }
                } catch (IllegalAccessException ignored) {
                    // If we can't access, skip field.
                }
            }
        }
    }

    /**
     * Returns true when a field name suggests a date value.
     */
    private static boolean isDateFieldName(String fieldName) {
        if (fieldName == null) return false;
        String lowercaseFieldName = fieldName.toLowerCase(Locale.ROOT);
        return lowercaseFieldName.contains("date") || lowercaseFieldName.equals("dob");
    }

    /**
     * Returns true when a map key suggests a date value.
     */
    private static boolean isDateKey(String key) {
        if (key == null) return false;
        String lowercaseKey = key.toLowerCase(Locale.ROOT);
        return lowercaseKey.contains("date") || lowercaseKey.equals("dob");
    }

    /**
     * Normalizes a date-like value based on its field/key name.
     */
    private static String normalizeDateValue(String name, String value) {
        if (value == null) return null;
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);

        if (lower.contains("expire") && lower.contains("date")) {
            return FormatDateHelper.normalizeExpiredDateForFilter(value);
        }

        return FormatDateHelper.normalizeDateOrNull(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void putMapValue(Map<?, ?> map, Object key, Object value) {
        ((Map) map).put(key, value);
    }
}
