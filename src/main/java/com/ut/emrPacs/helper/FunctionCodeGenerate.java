package com.ut.emrPacs.helper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class FunctionCodeGenerate {
    public static final DateTimeFormatter PATIENT_YEAR_FORMAT = DateTimeFormatter.ofPattern("yy");
    public static final DateTimeFormatter VISIT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    public static final int PATIENT_SEQUENCE_DIGITS = 7;
    public static final int VISIT_SEQUENCE_DIGITS = 4;

    private FunctionCodeGenerate() {
    }

    public static String currentPatientYearPrefix() {
        return LocalDate.now().format(PATIENT_YEAR_FORMAT);
    }

    public static String currentVisitDateToken() {
        return LocalDate.now().format(VISIT_DATE_FORMAT);
    }

    public static String buildPatientCode(String yearPrefix, String hospitalToken, long sequence) {
        String safeYear = FunctionHelper.hasText(yearPrefix) ? yearPrefix.trim() : currentPatientYearPrefix();
        String safeHospital = FunctionHelper.hasText(hospitalToken) ? hospitalToken.trim().toUpperCase() : "HOSP";
        long safeSequence = Math.max(sequence, 1L);
        return safeYear + "-" + safeHospital + "-P" + leftPad(safeSequence, PATIENT_SEQUENCE_DIGITS);
    }

    public static String buildPatientSequenceKey(String yearPrefix, String hospitalToken) {
        String safeYear = FunctionHelper.hasText(yearPrefix) ? yearPrefix.trim() : currentPatientYearPrefix();
        String safeHospital = FunctionHelper.hasText(hospitalToken) ? hospitalToken.trim().toUpperCase() : "HOSP";
        return safeYear + "-" + safeHospital;
    }

    public static String buildVisitCode(String modalityToken, String hospitalToken, String dateToken, long sequence) {
        String safeModality = cleanToken(modalityToken, "OT");
        String safeHospital = cleanToken(hospitalToken, "HOSP");
        String safeDate = shortVisitDateToken(dateToken);
        long safeSequence = Math.max(sequence, 1L);
        return safeModality + "-" + safeHospital + "-" + safeDate + "-" + leftPad(safeSequence, VISIT_SEQUENCE_DIGITS);
    }

    public static String buildVisitSequenceKey(String modalityToken, String hospitalToken, String dateToken) {
        String safeModality = FunctionHelper.hasText(modalityToken) ? modalityToken.trim().toUpperCase() : "OT";
        String safeHospital = FunctionHelper.hasText(hospitalToken) ? hospitalToken.trim().toUpperCase() : "HOSP";
        String safeDate = FunctionHelper.hasText(dateToken) ? dateToken.trim() : currentVisitDateToken();
        return safeModality + "-" + safeHospital + "-" + safeDate;
    }

    private static String leftPad(long sequence, int digits) {
        return String.format("%0" + digits + "d", sequence);
    }

    private static String shortVisitDateToken(String dateToken) {
        String safeDate = FunctionHelper.hasText(dateToken) ? dateToken.trim() : currentVisitDateToken();
        String digitsOnly = safeDate.replaceAll("\\D", "");
        if (digitsOnly.length() >= 8) {
            return digitsOnly.substring(2, 8);
        }
        if (digitsOnly.length() >= 6) {
            return digitsOnly.substring(0, 6);
        }
        return String.format("%-6s", digitsOnly).replace(' ', '0');
    }

    private static String cleanToken(String value, String fallback) {
        String source = FunctionHelper.hasText(value) ? value.trim().toUpperCase() : fallback;
        String normalized = source.replaceAll("[^A-Z0-9]", "");
        if (normalized.isBlank()) {
            normalized = fallback;
        }
        return normalized;
    }
}
