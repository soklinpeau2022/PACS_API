package com.ut.emrPacs.helper.security;

import java.util.regex.Pattern;

public final class UnicodeGuard {

    private static final Pattern DISALLOWED = Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F" +
                    "\\u202A-\\u202E\\u2066-\\u2069" +
                    "\\u200E\\u200F\\u061C" +
                    "\\u200B-\\u200D\\uFEFF" +
                    "\\u2028\\u2029]"
    );

    private UnicodeGuard() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean containsDisallowed(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return DISALLOWED.matcher(value).find();
    }
}
