package com.ut.emrPacs.helper.dicomServer;

import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerStudyResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import static com.ut.emrPacs.helper.FunctionHelper.firstNonNull;
import static com.ut.emrPacs.helper.FunctionHelper.hasText;

/**
 * Stateless utilities for reading values out of DICOM-server (Orthanc-style) responses:
 * main DICOM tags, study dates, instance statistics and series ids.
 *
 * <p>Extracted from {@code WorklistServiceImpl} to keep that service focused on orchestration.</p>
 */
public final class DicomResponseReadHelper {

    private DicomResponseReadHelper() {
    }

    /** Reads a main DICOM tag value from a study response, or {@code null}. */
    public static String readDicomTag(DicomServerStudyResponse response, String tag) {
        if (response == null) {
            return null;
        }
        return readDicomTag(response.getMainDicomTags(), tag);
    }

    /** Reads a DICOM tag value from a raw tag map, or {@code null}. */
    public static String readDicomTag(Map<String, Object> dicomTags, String tag) {
        if (dicomTags == null || tag == null) {
            return null;
        }
        Object value = dicomTags.get(tag);
        return value == null ? null : String.valueOf(value);
    }

    /** Parses a DICOM study date ({@code yyyyMMdd} or ISO) to {@link LocalDate}, or {@code null}. */
    public static LocalDate parseDicomStudyDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        try {
            if (normalized.length() == 8 && normalized.chars().allMatch(Character::isDigit)) {
                return LocalDate.parse(normalized, DateTimeFormatter.BASIC_ISO_DATE);
            }
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /** Reads the instance count from a DICOM-server statistics map across known key variants, or {@code null}. */
    public static Integer readDicomServerInstanceCount(Map<String, Object> statistics) {
        if (statistics == null || statistics.isEmpty()) {
            return null;
        }
        Object value = firstNonNull(
                statistics.get("CountInstances"),
                statistics.get("Instances"),
                statistics.get("TotalInstances"),
                statistics.get("InstanceCount")
        );
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** Returns the first non-blank, trimmed series id, or {@code ""}. */
    public static String firstDicomServerSeriesId(List<String> seriesIds) {
        if (seriesIds == null || seriesIds.isEmpty()) {
            return "";
        }
        for (String value : seriesIds) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    /** Puts {@code key -> value.trim()} into {@code values} only when both key and value have text. */
    public static void putIfHasText(Map<String, Object> values, String key, String value) {
        if (values != null && hasText(key) && hasText(value)) {
            values.put(key, value.trim());
        }
    }

    /** Returns the trimmed value, or {@code ""} when {@code null}. */
    public static String normalizedOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
