package com.ut.emrPacs.helper.security;

import java.util.regex.Pattern;

public final class SecurityInputSanitizerHelper {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

    private SecurityInputSanitizerHelper() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }

        // Strip control characters (null bytes, carriage returns used for header injection, etc.)
        String sanitized = CONTROL_CHARS.matcher(value).replaceAll("");

        // Neutralize HTML/XSS tokens
        sanitized = sanitized.replace("&", "&amp;");   // must be first to avoid double-encoding
        sanitized = sanitized.replace("<", "&lt;");
        sanitized = sanitized.replace(">", "&gt;");
        sanitized = sanitized.replace("\"", "&quot;");
        sanitized = sanitized.replace("'", "&#x27;");
        sanitized = sanitized.replace("`", "&#x60;");

        // Neutralize template/EL injection tokens
        sanitized = sanitized.replace("${", "$\\{");
        sanitized = sanitized.replace("#{", "#\\{");
        sanitized = sanitized.replace("@{", "@\\{");
        sanitized = sanitized.replace("{{", "{\\{");

        // Neutralize path traversal
        sanitized = sanitized.replace("../", ".._/");
        sanitized = sanitized.replace("..\\", ".._\\");

        // Neutralize HTTP response-splitting characters
        sanitized = sanitized.replace("\r", "");
        sanitized = sanitized.replace("\n", "");

        return sanitized;
    }
}

