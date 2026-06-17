package com.ut.emrPacs.config;

import com.ut.emrPacs.helper.security.SecurityIncidentReporter;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, org.apache.ibatis.cache.CacheKey.class, BoundSql.class})
})
public class MyBatisSqlInjectionGuardInterceptor implements Interceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyBatisSqlInjectionGuardInterceptor.class);

    private static final int MAX_ORDER_BY_LENGTH = 160;
    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final int MAX_ID_LIST_LENGTH = 4096;

    private static final String MAPPER_RESOURCE_PATTERN = "classpath*:mybatis/**/*.xml";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private static final Pattern UNSAFE_TOKEN_PATTERN = Pattern.compile("(?i)(;|--|/\\*|\\*/|\\b(drop|truncate|alter|create|grant|revoke|union\\s+select|sleep\\s*\\(|benchmark\\s*\\()\\b)");
    private static final Pattern ORDER_ITEM_PATTERN = Pattern.compile("(?i)^`?[A-Za-z0-9_]+`?(?:\\.`?[A-Za-z0-9_]+`?)?(?:\\s+(ASC|DESC))?$");
    private static final Pattern ORDER_DIRECTION_PATTERN = Pattern.compile("(?i)^(ASC|DESC)$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Pattern QUALIFIED_IDENTIFIER_PATTERN = Pattern.compile("^`?[A-Za-z0-9_]+`?(?:\\.`?[A-Za-z0-9_]+`?)?$");
    private static final Pattern ID_LIST_PATTERN = Pattern.compile("^\\d+(\\s*,\\s*\\d+)*$");

    private static final Map<String, DynamicKeyRule> DYNAMIC_KEY_RULES = buildDynamicKeyRules();

    private final Set<String> dynamicKeys;
    private final SecurityIncidentReporter securityIncidentReporter;

    public MyBatisSqlInjectionGuardInterceptor() {
        this(null);
    }

    @Autowired
    public MyBatisSqlInjectionGuardInterceptor(@Lazy SecurityIncidentReporter securityIncidentReporter) {
        this.dynamicKeys = loadDynamicKeys();
        this.securityIncidentReporter = securityIncidentReporter;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement mappedStatement = (MappedStatement) args[0];
        String statementId = mappedStatement != null ? mappedStatement.getId() : "unknown";

        if (args.length > 1) {
            Object parameterObject = args[1];
            IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
            try {
                validateDynamicValues(parameterObject, statementId, visited);
            } catch (IllegalArgumentException ex) {
                reportBlockedSql(statementId, ex.getMessage());
                throw ex;
            }
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    private void validateDynamicValues(Object value, String statementId, IdentityHashMap<Object, Boolean> visited) {
        if (value == null || isSimpleType(value.getClass())) {
            return;
        }

        if (visited.put(value, Boolean.TRUE) != null) {
            return;
        }

        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                Object entryValue = entry.getValue();
                validateByKey(key, entryValue, statementId);
                validateDynamicValues(entryValue, statementId, visited);
            }
            return;
        }

        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                validateDynamicValues(item, statementId, visited);
            }
            return;
        }

        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                validateDynamicValues(Array.get(value, index), statementId, visited);
            }
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
                validateByKey(field.getName(), fieldValue, statementId);
                validateDynamicValues(fieldValue, statementId, visited);
            }
        }
    }

    private void validateByKey(String key, Object value, String statementId) {
        if (!(value instanceof String stringValue)) {
            return;
        }

        String trimmed = stringValue.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        String normalizedKey = normalizeKey(key);
        if (!dynamicKeys.contains(normalizedKey)) {
            return;
        }

        DynamicKeyRule rule = DYNAMIC_KEY_RULES.get(normalizedKey);
        if (rule == null) {
            throw reject(statementId, key);
        }

        if (trimmed.length() > MAX_ID_LIST_LENGTH) {
            throw reject(statementId, key);
        }

        assertNoUnsafeTokens(trimmed, statementId, key);

        switch (rule) {
            case ORDER_BY -> {
                if (trimmed.length() > MAX_ORDER_BY_LENGTH) {
                    throw reject(statementId, key);
                }
                validateOrderBy(trimmed, statementId, key);
            }
            case ORDER_DIRECTION -> validateOrderDirection(trimmed, statementId, key);
            case ID_LIST -> validateIdList(trimmed, statementId, key);
            case TABLE -> {
                if (trimmed.length() > MAX_IDENTIFIER_LENGTH) {
                    throw reject(statementId, key);
                }
                validateIdentifier(trimmed, statementId, key);
            }
            case IDENTIFIER -> {
                if (trimmed.length() > MAX_IDENTIFIER_LENGTH) {
                    throw reject(statementId, key);
                }
                validateQualifiedIdentifier(trimmed, statementId, key);
            }
        }
    }

    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || Number.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || Enum.class.isAssignableFrom(type)
                || Date.class.isAssignableFrom(type)
                || type.getName().startsWith("java.time.");
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        String trimmed = key.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int dot = trimmed.lastIndexOf('.');
        if (dot >= 0 && dot < trimmed.length() - 1) {
            trimmed = trimmed.substring(dot + 1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static Map<String, DynamicKeyRule> buildDynamicKeyRules() {
        Map<String, DynamicKeyRule> rules = new LinkedHashMap<>();
        rules.put("orderby", DynamicKeyRule.ORDER_BY);
        rules.put("field", DynamicKeyRule.IDENTIFIER);
        rules.put("fieldfree", DynamicKeyRule.IDENTIFIER);
        rules.put("modulecolumn", DynamicKeyRule.IDENTIFIER);
        rules.put("descriptioncolumn", DynamicKeyRule.IDENTIFIER);
        rules.put("typenamecol", DynamicKeyRule.IDENTIFIER);
        rules.put("periodkeycol", DynamicKeyRule.IDENTIFIER);
        rules.put("table", DynamicKeyRule.TABLE);
        rules.put("tablename", DynamicKeyRule.TABLE);
        rules.put("tablenamedetail", DynamicKeyRule.TABLE);
        rules.put("ledgertablename", DynamicKeyRule.TABLE);
        rules.put("locationgroupid", DynamicKeyRule.TABLE);
        rules.put("locationid", DynamicKeyRule.TABLE);
        rules.put("locid", DynamicKeyRule.TABLE);
        rules.put("warehouseid", DynamicKeyRule.TABLE);
        rules.put("id", DynamicKeyRule.TABLE);
        return Collections.unmodifiableMap(rules);
    }

    private static Set<String> loadDynamicKeys() {
        Set<String> keys = new LinkedHashSet<>();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(MAPPER_RESOURCE_PATTERN);
            for (Resource resource : resources) {
                keys.addAll(extractDynamicKeys(resource));
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to scan MyBatis mapper XML for ${} placeholders: {}", PLACEHOLDER_PATTERN.pattern(), ex.toString());
        }

        if (keys.isEmpty()) {
            LOGGER.warn("No MyBatis ${} placeholders found; falling back to built-in allowlist.", PLACEHOLDER_PATTERN.pattern());
            return Collections.unmodifiableSet(new LinkedHashSet<>(DYNAMIC_KEY_RULES.keySet()));
        }

        Set<String> unknown = new LinkedHashSet<>();
        for (String key : keys) {
            if (!DYNAMIC_KEY_RULES.containsKey(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            LOGGER.error("MyBatis ${} placeholders without allowlist rules: {}", PLACEHOLDER_PATTERN.pattern(), unknown);
        }

        return Collections.unmodifiableSet(keys);
    }

    private static Set<String> extractDynamicKeys(Resource resource) {
        Set<String> keys = new LinkedHashSet<>();
        if (resource == null || !resource.exists()) {
            return keys;
        }
        try (InputStream in = resource.getInputStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
            while (matcher.find()) {
                String normalized = normalizeKey(matcher.group(1));
                if (!normalized.isEmpty()) {
                    keys.add(normalized);
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to read MyBatis mapper {}: {}", resource.getDescription(), ex.toString());
        }
        return keys;
    }

    private static void assertNoUnsafeTokens(String value, String statementId, String key) {
        if (UNSAFE_TOKEN_PATTERN.matcher(value).find()) {
            throw reject(statementId, key);
        }
    }

    private static void validateOrderBy(String value, String statementId, String key) {
        String[] parts = value.split(",");
        for (String part : parts) {
            String token = part == null ? "" : part.trim();
            if (token.isEmpty() || !ORDER_ITEM_PATTERN.matcher(token).matches()) {
                throw reject(statementId, key);
            }
        }
    }

    private static void validateOrderDirection(String value, String statementId, String key) {
        if (!ORDER_DIRECTION_PATTERN.matcher(value).matches()) {
            throw reject(statementId, key);
        }
    }

    private static void validateIdentifier(String value, String statementId, String key) {
        if (!IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw reject(statementId, key);
        }
    }

    private static void validateQualifiedIdentifier(String value, String statementId, String key) {
        if (!QUALIFIED_IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw reject(statementId, key);
        }
    }

    private static void validateIdList(String value, String statementId, String key) {
        if (!ID_LIST_PATTERN.matcher(value).matches()) {
            throw reject(statementId, key);
        }
    }

    private static IllegalArgumentException reject(String statementId, String key) {
        return new IllegalArgumentException("Rejected unsafe dynamic SQL parameter for " + statementId + " (" + key + ")");
    }

    private void reportBlockedSql(String statementId, String detail) {
        if (securityIncidentReporter != null) {
            securityIncidentReporter.reportBlockedRequest(
                    null,
                    "sql_injection_guard",
                    "unsafe_dynamic_sql",
                    (statementId == null || statementId.isBlank() ? "unknown" : statementId) + " " + detail
            );
        }
    }

    private enum DynamicKeyRule {
        ORDER_BY,
        ORDER_DIRECTION,
        ID_LIST,
        TABLE,
        IDENTIFIER
    }
}
