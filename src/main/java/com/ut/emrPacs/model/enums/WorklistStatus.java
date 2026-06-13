package com.ut.emrPacs.model.enums;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import static java.util.Map.entry;

public enum WorklistStatus {
    WAITING(1),
    IN_PROGRESS(2),
    CANCELLED(3),
    FAILED(4);

    private final int code;

    WorklistStatus(int code) {
        this.code = code;
    }

    private static final Map<WorklistStatus, EnumSet<WorklistStatus>> ALLOWED_TRANSITIONS = Map.ofEntries(
            entry(WAITING, EnumSet.of(IN_PROGRESS, CANCELLED, FAILED)),
            entry(IN_PROGRESS, EnumSet.of(FAILED)),
            entry(CANCELLED, EnumSet.noneOf(WorklistStatus.class)),
            entry(FAILED, EnumSet.of(WAITING, IN_PROGRESS, CANCELLED))
    );

    public boolean canTransitionTo(WorklistStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(WorklistStatus.class)).contains(target);
    }

    public int code() {
        return code;
    }

    public static WorklistStatus fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Worklist status is required.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "CANCELED" -> CANCELLED;
            default -> WorklistStatus.valueOf(normalized);
        };
    }

    public static WorklistStatus fromCode(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("Worklist status code is required.");
        }
        switch (code) {
            case 1 -> {
                return WAITING;
            }
            case 2 -> {
                return IN_PROGRESS;
            }
            case 3 -> {
                return CANCELLED;
            }
            case 4 -> {
                return FAILED;
            }
            default -> throw new IllegalArgumentException("Unknown Worklist status code: " + code);
        }
    }

    public static Integer codeOfNullable(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            int parsed = Integer.parseInt(trimmed);
            return fromCode(parsed).code();
        }
        return fromValue(trimmed).code();
    }
}
