package com.ut.emrPacs.helper.date;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Date formatting/normalization utilities.
 *
 * <p>This project frequently stores dates as strings and uses {@code "0000-00-00"}
 * as a sentinel for "no date" (especially for inventory expiry fields).</p>
 */
public final class FormatDateHelper {

    /**
     * Legacy/sentinel date value used across inventory modules.
     */
    public static final String ZERO_DATE = "0000-00-00";

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd
    private static final List<DateTimeFormatter> DATE_INPUT_FORMATS = List.of(
            ISO_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy")
    );

    private FormatDateHelper() {
    }

    /**
     * Normalizes an expired date to {@code yyyy-MM-dd} where possible, or returns {@link #ZERO_DATE}.
     */
    public static String normalizeExpiredDate(String expiredDate) {
        return normalizeDate(expiredDate);
    }

    /**
     * Normalizes an expiry-date filter value:
     * <ul>
     *   <li>blank/null -> null (so MyBatis can skip the filter)</li>
     *   <li>{@code 0000-00-00} -> {@code 0000-00-00}</li>
     *   <li>otherwise -> ISO date if parsable, else null</li>
     * </ul>
     */
    public static String normalizeExpiredDateForFilter(String expiredDate) {
        if (expiredDate == null) return null;
        String trimmed = expiredDate.trim();
        if (trimmed.isEmpty()) return null;
        if (ZERO_DATE.equals(trimmed)) return ZERO_DATE;

        LocalDate parsed = parseToLocalDate(trimmed);
        return parsed == null ? null : parsed.format(ISO_DATE);
    }

    /**
     * Normalizes a general date value for persistence layers.
     *
     * <p>Returns {@link #ZERO_DATE} for null/blank/invalid inputs.</p>
     */
    public static String normalizeDate(String date) {
        if (date == null) return ZERO_DATE;
        String trimmed = date.trim();
        if (trimmed.isEmpty()) return ZERO_DATE;
        if (ZERO_DATE.equals(trimmed)) return ZERO_DATE;

        LocalDate parsed = parseToLocalDate(trimmed);
        return parsed == null ? ZERO_DATE : parsed.format(ISO_DATE);
    }

    /**
     * Normalizes a date-like filter value:
     * <ul>
     *   <li>blank/null -> null</li>
     *   <li>{@code 0000-00-00} -> null (treat as "no filter")</li>
     *   <li>otherwise -> ISO date if parsable, else null</li>
     * </ul>
     */
    public static String normalizeDateOrNull(String date) {
        if (date == null) return null;
        String trimmed = date.trim();
        if (trimmed.isEmpty()) return null;
        if (ZERO_DATE.equals(trimmed)) return null;

        LocalDate parsed = parseToLocalDate(trimmed);
        return parsed == null ? null : parsed.format(ISO_DATE);
    }

    /**
     * Validates that {@code date} is a real ISO date ({@code yyyy-MM-dd}), excluding {@link #ZERO_DATE}.
     */
    public static boolean isValidIsoDate(String date) {
        if (date == null) return false;
        String trimmed = date.trim();
        if (trimmed.isEmpty() || ZERO_DATE.equals(trimmed)) return false;
        try {
            LocalDate.parse(trimmed, ISO_DATE);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private static LocalDate parseToLocalDate(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;

        // Accept date-time strings like "yyyy-MM-dd HH:mm:ss" or ISO "yyyy-MM-ddTHH:mm:ss"
        if (v.length() >= 10) {
            String datePart = v.substring(0, 10);
            if (datePart.matches("\\d{4}-\\d{2}-\\d{2}")) {
                try {
                    return LocalDate.parse(datePart, ISO_DATE);
                } catch (DateTimeParseException ignored) {
                    // fallthrough
                }
            }
        }

        for (DateTimeFormatter formatter : DATE_INPUT_FORMATS) {
            try {
                return LocalDate.parse(v, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }
}
