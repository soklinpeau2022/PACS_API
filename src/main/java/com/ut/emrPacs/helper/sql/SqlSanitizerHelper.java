package com.ut.emrPacs.helper.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Centralized sanitizers for values that end up in SQL string substitution (MyBatis ${...}).
 *
 * <p>IMPORTANT: Prefer MyBatis #{...} whenever possible. These helpers are only for
 * unavoidable identifier injection like ORDER BY columns or dynamic table names.</p>
 */
public final class SqlSanitizerHelper {

    private static final int MAX_ORDER_BY_LENGTH = 160;
    private static final int MAX_IDENTIFIER_LENGTH = 128;

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("^[A-Za-z0-9_]+$");

    private SqlSanitizerHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Sanitizes a client-provided ORDER BY string.
     *
     * <p>Allowed forms:
     * <ul>
     *   <li>{@code col}</li>
     *   <li>{@code col ASC|DESC}</li>
     *   <li>{@code t.col DESC}</li>
     *   <li>multiple columns: {@code col1 DESC, col2 ASC}</li>
     * </ul>
     *
     * <p>Returns {@code null} when invalid (caller should fall back to a safe default ORDER BY).</p>
     */
    public static String sanitizeOrderBy(String raw) {
        if (raw == null) {
            return null;
        }

        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_ORDER_BY_LENGTH) {
            return null;
        }

        // Block obvious injection tokens.
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains(";")
                || lower.contains("--")
                || lower.contains("/*")
                || lower.contains("*/")
                || lower.contains("#")
                || lower.contains("\n")
                || lower.contains("\r")
                || lower.contains("\t")) {
            return null;
        }

        String normalized = trimmed.replace('`', ' ').trim();
        String[] parts = normalized.split(",");
        List<String> safeParts = new ArrayList<>(parts.length);

        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) {
                return null;
            }

            String[] tokens = p.split("\\s+");
            if (tokens.length < 1 || tokens.length > 2) {
                return null;
            }

            String identifier = tokens[0].trim();
            if (isInvalidDottedIdentifier(identifier)) {
                return null;
            }

            String direction = null;
            if (tokens.length == 2) {
                direction = tokens[1].trim().toUpperCase(Locale.ROOT);
                if (!direction.equals("ASC") && !direction.equals("DESC")) {
                    return null;
                }
            }

            safeParts.add(direction == null ? identifier : (identifier + " " + direction));
        }

        return String.join(", ", safeParts);
    }

    /**
     * Ensures a dynamic table name is safe for use in SQL identifier substitution.
     *
     * <p>Returns a normalized (trimmed, no backticks) table name or throws {@link IllegalArgumentException}.</p>
     */
    public static String requireSafeTableName(String tableName) {
        if (tableName == null) {
            throw new IllegalArgumentException("tableName is null");
        }
        String trimmed = tableName.replace("`", "").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("tableName is blank");
        }
        if (trimmed.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException("tableName too long");
        }
        if (!SAFE_TABLE_NAME.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid tableName: " + trimmed);
        }
        return trimmed;
    }

    /**
     * Ensures a dynamic identifier (column name, alias, etc.) is safe for SQL identifier substitution.
     *
     * <p>Returns a normalized (trimmed, no backticks) identifier or throws {@link IllegalArgumentException}.</p>
     */
    public static String requireSafeIdentifier(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier is null");
        }
        String trimmed = identifier.replace("`", "").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("identifier is blank");
        }
        if (trimmed.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException("identifier too long");
        }
        if (!SAFE_IDENTIFIER.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid identifier: " + trimmed);
        }
        return trimmed;
    }

    /**
     * Ensures a dotted identifier is safe for SQL identifier substitution.
     *
     * <p>Allowed:
     * <ul>
     *   <li>{@code col}</li>
     *   <li>{@code t.col}</li>
     * </ul>
     */
    public static String requireSafeDottedIdentifier(String identifier) {
        if (isInvalidDottedIdentifier(identifier)) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }
        return identifier.trim();
    }

    private static boolean isInvalidDottedIdentifier(String value) {
        if (value == null) {
            return true;
        }
        String v = value.trim();
        if (v.isEmpty() || v.length() > MAX_IDENTIFIER_LENGTH) {
            return true;
        }
        if (v.contains("..")) {
            return true;
        }
        String[] segments = v.split("\\.");
        for (String seg : segments) {
            if (seg.isEmpty() || !SAFE_IDENTIFIER.matcher(seg).matches()) {
                return true;
            }
        }
        return false;
    }
}
