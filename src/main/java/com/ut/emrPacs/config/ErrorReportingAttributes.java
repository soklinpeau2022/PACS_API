package com.ut.emrPacs.config;

import jakarta.servlet.http.HttpServletRequest;

public final class ErrorReportingAttributes {

    private static final String PREFIX = ErrorReportingAttributes.class.getName();

    public static final String ERROR_ACTIVITY_LOGGED = PREFIX + ".errorActivityLogged";
    public static final String ERROR_TELEGRAM_ALERTED = PREFIX + ".errorTelegramAlerted";

    private ErrorReportingAttributes() {
    }

    public static boolean isErrorActivityLogged(HttpServletRequest request) {
        return hasAttribute(request, ERROR_ACTIVITY_LOGGED);
    }

    public static void markErrorActivityLogged(HttpServletRequest request) {
        setAttribute(request, ERROR_ACTIVITY_LOGGED);
    }

    public static boolean isErrorTelegramAlerted(HttpServletRequest request) {
        return hasAttribute(request, ERROR_TELEGRAM_ALERTED);
    }

    public static void markErrorTelegramAlerted(HttpServletRequest request) {
        setAttribute(request, ERROR_TELEGRAM_ALERTED);
    }

    private static boolean hasAttribute(HttpServletRequest request, String attribute) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(attribute));
    }

    private static void setAttribute(HttpServletRequest request, String attribute) {
        if (request != null) {
            request.setAttribute(attribute, Boolean.TRUE);
        }
    }
}
