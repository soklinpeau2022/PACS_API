package com.ut.emrPacs.authentication.util;

import java.util.Locale;

/**
 * Central password policy for all password set/reset flows.
 *
 * <p>Security goals:</p>
 * <ul>
 *   <li>Minimum length to resist offline cracking</li>
 *   <li>Require multiple character classes</li>
 *   <li>Reject whitespace/control characters</li>
 * </ul>
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns {@code null} when the password is acceptable, otherwise an error message.
     */
    public static String validate(String password) {
        if (password == null) {
            return "Password is required.";
        }

        String p = password;
        if (p.isBlank()) {
            return "Password is required.";
        }
        if (p.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters.";
        }
        if (p.length() > MAX_LENGTH) {
            return "Password is too long.";
        }
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                return "Password must not contain spaces or control characters.";
            }
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;

        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c >= 'A' && c <= 'Z') hasUpper = true;
            else if (c >= 'a' && c <= 'z') hasLower = true;
            else if (c >= '0' && c <= '9') hasDigit = true;
            else hasSymbol = true;
        }

        if (!(hasUpper && hasLower && hasDigit && hasSymbol)) {
            return "Password must include uppercase, lowercase, a number, and a symbol.";
        }

        // Avoid extremely common passwords (small denylist for baseline safety).
        String lower = p.toLowerCase(Locale.ROOT);
        if (lower.equals("password") || lower.equals("password123") || lower.equals("1234567890")) {
            return "Password is too common.";
        }

        return null;
    }
}

