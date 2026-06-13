package com.ut.emrPacs.helper;

import java.util.Locale;

public final class FunctionHelper {
    private FunctionHelper() {
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * Returns the first non-blank trimmed value, or {@code null} if none found.
     * Use this when a {@code null} sentinel is needed instead of an empty string.
     */
    public static String firstNonBlankOrNull(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * Returns the first non-null value among {@code values}, or {@code null} if all are null.
     */
    @SafeVarargs
    public static <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static String normalizeHospitalToken(String rawHospital) {
        String normalized = rawHospital == null ? "" : rawHospital.trim().toUpperCase(Locale.ROOT);
        if (!hasText(normalized)) {
            return "";
        }
        String[] tokens = normalized.split("[^A-Z0-9]+");
        for (String token : tokens) {
            if (token.matches("[A-Z0-9]{2,20}") && !isGenericHospitalToken(token)) {
                return token;
            }
        }
        String compact = normalized.replaceAll("[^A-Z0-9]", "");
        if (compact.length() > 20) {
            compact = compact.substring(0, 20);
        }
        return compact;
    }

    public static boolean isValidHospitalToken(String token) {
        return token != null && token.matches("[A-Z0-9]{2,20}") && !isGenericHospitalToken(token);
    }

    public static String normalizeModalityToken(String rawModality) {
        String normalized = rawModality == null ? "" : rawModality.trim().toUpperCase(Locale.ROOT);
        if (normalized.matches("[A-Z0-9]{2,8}")) {
            return normalized;
        }
        if (normalized.contains("CT")) return "CT";
        if (normalized.contains("MRI") || normalized.equals("MR")) return "MRI";
        if (normalized.contains("XRAY") || normalized.equals("X-RAY") || normalized.equals("XR")) return "CR";
        if (normalized.contains("DX")) return "DX";
        if (normalized.contains("US") || normalized.contains("ULTRASOUND") || normalized.contains("ECHO")) return "US";
        if (normalized.contains("MG")) return "MG";
        if (normalized.contains("NM")) return "NM";
        if (normalized.contains("PET") || normalized.equals("PT")) return "PT";
        return "OT";
    }

    private static boolean isGenericHospitalToken(String token) {
        return "HOSPITAL".equals(token) || "HOSP".equals(token) || "CLINIC".equals(token) || "CENTER".equals(token);
    }
}
