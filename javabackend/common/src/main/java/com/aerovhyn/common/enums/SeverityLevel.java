package com.aerovhyn.common.enums;

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
}
