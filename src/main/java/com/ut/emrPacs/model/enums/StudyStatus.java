package com.ut.emrPacs.model.enums;

import java.util.Locale;

public enum StudyStatus {
    IMAGE_RECEIVED(1),
    COMPLETED(2);

    private final int code;

    StudyStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static StudyStatus fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Study status is required.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "IMAGE", "RECEIVED" -> IMAGE_RECEIVED;
            default -> StudyStatus.valueOf(normalized);
        };
    }

    public static StudyStatus fromCode(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("Study status code is required.");
        }
        return switch (code) {
            case 1 -> IMAGE_RECEIVED;
            case 2 -> COMPLETED;
            default -> throw new IllegalArgumentException("Unknown study status code: " + code);
        };
    }

    public static Integer codeOfNullable(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            return fromCode(Integer.parseInt(trimmed)).code();
        }
        return fromValue(trimmed).code();
    }
}
