package com.aerovhyn.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SeverityLevel {
    CRITICAL("critical"),
    MODERATE("moderate"),
    STABLE("stable");

    private final String value;

    SeverityLevel(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SeverityLevel fromValue(String value) {
        for (SeverityLevel level : values()) {
            if (level.value.equalsIgnoreCase(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown severity level: " + value);
    }
}
