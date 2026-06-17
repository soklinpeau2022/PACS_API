package com.ut.emrPacs.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationSafetyPolicyTest {

    private static final int SAFETY_POLICY_START_VERSION = 180;
    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");
    private static final Pattern MIGRATION_FILE = Pattern.compile("^V(\\d+(?:_\\d+)*)__.+\\.sql$");
    private static final Pattern CREATE_TABLE_WITHOUT_IF_NOT_EXISTS = Pattern.compile(
            "(?is)^\\s*CREATE\\s+(?:TEMP(?:ORARY)?\\s+)?TABLE\\s+(?!IF\\s+NOT\\s+EXISTS\\b)");
    private static final Pattern CREATE_INDEX_WITHOUT_IF_NOT_EXISTS = Pattern.compile(
            "(?is)^\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+(?:CONCURRENTLY\\s+)?(?!IF\\s+NOT\\s+EXISTS\\b)");
    private static final Pattern ADD_COLUMN_WITHOUT_IF_NOT_EXISTS = Pattern.compile(
            "(?is)\\bADD\\s+COLUMN\\s+(?!IF\\s+NOT\\s+EXISTS\\b)");
    private static final Pattern ADD_NOT_NULL_COLUMN = Pattern.compile(
            "(?is)\\bADD\\s+COLUMN\\b[^;]*\\bNOT\\s+NULL\\b");
    private static final Pattern SET_NOT_NULL = Pattern.compile(
            "(?is)\\bALTER\\s+COLUMN\\b[^;]*\\bSET\\s+NOT\\s+NULL\\b");
    private static final Pattern ADD_CONSTRAINT = Pattern.compile(
            "(?is)^\\s*ALTER\\s+TABLE\\b[^;]*\\bADD\\s+CONSTRAINT\\b");
    private static final Pattern DROP_CONSTRAINT_WITHOUT_IF_EXISTS = Pattern.compile(
            "(?is)^\\s*ALTER\\s+TABLE\\b[^;]*\\bDROP\\s+CONSTRAINT\\s+(?!IF\\s+EXISTS\\b)");
    private static final Pattern ALTER_COLUMN_TYPE = Pattern.compile(
            "(?is)\\bALTER\\s+COLUMN\\b[^;]*\\bTYPE\\b");
    private static final Pattern CREATE_SEQUENCE_WITHOUT_IF_NOT_EXISTS = Pattern.compile(
            "(?is)^\\s*CREATE\\s+SEQUENCE\\s+(?!IF\\s+NOT\\s+EXISTS\\b)");
    private static final Pattern DESTRUCTIVE_SCHEMA_CHANGE = Pattern.compile(
            "(?is)\\b(?:TRUNCATE\\s+(?:TABLE\\s+)?|DROP\\s+TABLE\\b|ALTER\\s+TABLE\\b[^;]*\\bDROP\\s+COLUMN\\b)");
    private static final Pattern DOLLAR_QUOTE = Pattern.compile("\\$[A-Za-z0-9_]*\\$");
    private static final Set<String> SEEDED_METADATA_TABLES = Set.of(
            "endpoint_permissions",
            "module_details",
            "module_types",
            "role_module_details"
    );

    @Test
    void migrationVersionsShouldBeValidAndUnique() throws IOException {
        List<Migration> migrations = migrations();
        Map<String, String> seen = new HashMap<>();
        List<String> failures = new ArrayList<>();

        for (Migration migration : migrations) {
            String duplicate = seen.putIfAbsent(migration.version(), migration.fileName());
            if (duplicate != null) {
                failures.add("Duplicate Flyway version V" + migration.version() + ": " + duplicate + " and " + migration.fileName());
            }
        }

        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    @Test
    void newMigrationsShouldFollowDataSafetyPolicy() throws IOException {
        List<String> failures = new ArrayList<>();

        for (Migration migration : migrations()) {
            if (migration.majorVersion() < SAFETY_POLICY_START_VERSION) {
                continue;
            }
            validateMigration(migration, failures);
        }

        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    private static void validateMigration(Migration migration, List<String> failures) {
        String sql = migration.sql();
        String searchableSql = withoutComments(sql);
        List<String> statements = statements(searchableSql);
        boolean allowsDataLoss = hasMarker(sql, "allowed-data-loss");
        boolean allowsFullTableChange = hasMarker(sql, "full-table-change");
        boolean hasNotNullBackfillMarker = hasMarker(sql, "not-null-backfilled");
        boolean allowsUnguardedConstraint = hasMarker(sql, "constraint-guard-reviewed");
        boolean allowsTypeChange = hasMarker(sql, "type-change-reviewed");

        if (DESTRUCTIVE_SCHEMA_CHANGE.matcher(searchableSql).find() && !allowsDataLoss) {
            failures.add(migration.fileName()
                    + ": destructive schema changes require '-- MIGRATION-SAFETY: allowed-data-loss' and a rollback/backup note.");
        }

        for (String statement : statements) {
            String trimmed = statement.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String upper = trimmed.toUpperCase(Locale.ROOT);

            if (CREATE_TABLE_WITHOUT_IF_NOT_EXISTS.matcher(trimmed).find()) {
                failures.add(migration.fileName() + ": CREATE TABLE must use IF NOT EXISTS.");
            }
            if (upper.startsWith("CREATE TABLE") && !upper.contains("PRIMARY KEY") && !hasMarker(sql, "no-primary-key")) {
                failures.add(migration.fileName() + ": new tables should declare a primary key or use '-- MIGRATION-SAFETY: no-primary-key'.");
            }
            if (CREATE_INDEX_WITHOUT_IF_NOT_EXISTS.matcher(trimmed).find()) {
                failures.add(migration.fileName() + ": CREATE INDEX must use IF NOT EXISTS.");
            }
            if (CREATE_SEQUENCE_WITHOUT_IF_NOT_EXISTS.matcher(trimmed).find()) {
                failures.add(migration.fileName() + ": CREATE SEQUENCE must use IF NOT EXISTS.");
            }
            if (upper.startsWith("ALTER TABLE") && ADD_COLUMN_WITHOUT_IF_NOT_EXISTS.matcher(trimmed).find()) {
                failures.add(migration.fileName() + ": ADD COLUMN must use IF NOT EXISTS.");
            }
            if (ADD_NOT_NULL_COLUMN.matcher(trimmed).find() && !upper.contains(" DEFAULT ") && !hasNotNullBackfillMarker) {
                failures.add(migration.fileName()
                        + ": adding a NOT NULL column to an existing table needs a default or '-- MIGRATION-SAFETY: not-null-backfilled'.");
            }
            if (SET_NOT_NULL.matcher(trimmed).find() && (!hasNotNullBackfillMarker || !hasNullBackfill(statements))) {
                failures.add(migration.fileName()
                        + ": SET NOT NULL requires a prior NULL backfill and '-- MIGRATION-SAFETY: not-null-backfilled'.");
            }
            if (ADD_CONSTRAINT.matcher(trimmed).find() && !upper.contains("IF NOT EXISTS") && !allowsUnguardedConstraint) {
                failures.add(migration.fileName()
                        + ": ADD CONSTRAINT should be guarded by an existence check or '-- MIGRATION-SAFETY: constraint-guard-reviewed'.");
            }
            if (DROP_CONSTRAINT_WITHOUT_IF_EXISTS.matcher(trimmed).find() && !allowsUnguardedConstraint) {
                failures.add(migration.fileName()
                        + ": DROP CONSTRAINT must use IF EXISTS or '-- MIGRATION-SAFETY: constraint-guard-reviewed'.");
            }
            if (ALTER_COLUMN_TYPE.matcher(trimmed).find() && !allowsTypeChange) {
                failures.add(migration.fileName()
                        + ": ALTER COLUMN TYPE can rewrite or truncate data; use '-- MIGRATION-SAFETY: type-change-reviewed'.");
            }
            if (upper.startsWith("DELETE FROM") && !containsWhere(upper) && !allowsDataLoss) {
                failures.add(migration.fileName()
                        + ": DELETE must include WHERE or '-- MIGRATION-SAFETY: allowed-data-loss'.");
            }
            if (upper.startsWith("UPDATE ") && !containsWhere(upper) && !allowsFullTableChange) {
                failures.add(migration.fileName()
                        + ": full-table UPDATE requires '-- MIGRATION-SAFETY: full-table-change'.");
            }

            validateSeedInsert(migration, trimmed, upper, failures);
        }
    }

    private static void validateSeedInsert(Migration migration, String statement, String upper, List<String> failures) {
        if (!upper.startsWith("INSERT INTO")) {
            return;
        }

        Optional<String> tableName = insertedTableName(statement);
        if (tableName.isEmpty() || !SEEDED_METADATA_TABLES.contains(tableName.get())) {
            return;
        }

        if (!upper.contains("ON CONFLICT") && !upper.contains("WHERE NOT EXISTS")) {
            failures.add(migration.fileName()
                    + ": seed insert into " + tableName.get() + " must be idempotent with ON CONFLICT or WHERE NOT EXISTS.");
        }
    }

    private static Optional<String> insertedTableName(String statement) {
        Matcher matcher = Pattern.compile("(?is)^\\s*INSERT\\s+INTO\\s+([A-Za-z0-9_.\"]+)").matcher(statement);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String raw = matcher.group(1).replace("\"", "");
        int dot = raw.lastIndexOf('.');
        return Optional.of((dot >= 0 ? raw.substring(dot + 1) : raw).toLowerCase(Locale.ROOT));
    }

    private static boolean hasNullBackfill(List<String> statements) {
        return statements.stream()
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .anyMatch(value -> value.startsWith("UPDATE ") && value.contains(" IS NULL"));
    }

    private static boolean containsWhere(String upperStatement) {
        return Pattern.compile("(?is)\\bWHERE\\b").matcher(upperStatement).find();
    }

    private static boolean hasMarker(String sql, String marker) {
        return Pattern.compile("(?im)^\\s*--\\s*MIGRATION-SAFETY:\\s*" + Pattern.quote(marker) + "\\b")
                .matcher(sql)
                .find();
    }

    private static String withoutComments(String sql) {
        return sql
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--.*$", "");
    }

    private static List<String> statements(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        String dollarQuote = null;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);

            if (dollarQuote != null) {
                if (sql.startsWith(dollarQuote, i)) {
                    current.append(dollarQuote);
                    i += dollarQuote.length() - 1;
                    dollarQuote = null;
                } else {
                    current.append(ch);
                }
                continue;
            }

            if (inSingleQuote) {
                current.append(ch);
                if (ch == '\'' && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    current.append(sql.charAt(++i));
                } else if (ch == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }

            if (inDoubleQuote) {
                current.append(ch);
                if (ch == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }

            if (ch == '\'') {
                inSingleQuote = true;
                current.append(ch);
                continue;
            }
            if (ch == '"') {
                inDoubleQuote = true;
                current.append(ch);
                continue;
            }
            if (ch == '$') {
                Matcher matcher = DOLLAR_QUOTE.matcher(sql.substring(i));
                if (matcher.lookingAt()) {
                    dollarQuote = matcher.group();
                    current.append(dollarQuote);
                    i += dollarQuote.length() - 1;
                    continue;
                }
            }
            if (ch == ';') {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        if (!current.toString().isBlank()) {
            result.add(current.toString());
        }
        return result;
    }

    private static List<Migration> migrations() throws IOException {
        try (Stream<Path> paths = Files.list(MIGRATION_DIR)) {
            List<String> invalidNames = new ArrayList<>();
            List<Migration> migrations = new ArrayList<>();

            for (Path path : paths.filter(path -> path.getFileName().toString().endsWith(".sql")).toList()) {
                String fileName = path.getFileName().toString();
                Matcher matcher = MIGRATION_FILE.matcher(fileName);
                if (!matcher.matches()) {
                    invalidNames.add(fileName);
                    continue;
                }
                migrations.add(new Migration(
                        normalizeVersion(matcher.group(1)),
                        majorVersion(matcher.group(1)),
                        fileName,
                        Files.readString(path, StandardCharsets.UTF_8)
                ));
            }

            assertTrue(invalidNames.isEmpty(), () -> "Invalid Flyway migration file names: " + invalidNames);
            return migrations;
        }
    }

    private static String normalizeVersion(String rawVersion) {
        return rawVersion.replace('_', '.');
    }

    private static int majorVersion(String rawVersion) {
        int separator = rawVersion.indexOf('_');
        String major = separator >= 0 ? rawVersion.substring(0, separator) : rawVersion;
        return Integer.parseInt(major);
    }

    private record Migration(String version, int majorVersion, String fileName, String sql) {
    }
}
