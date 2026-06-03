package com.aerovhyn.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EmergencyType {
    CARDIAC("cardiac"),
    TRAUMA("trauma"),
    RESPIRATORY("respiratory"),
    NEUROLOGICAL("neurological"),
    FRACTURE("fracture"),
    BURN("burn"),
    GENERAL("general");

    private final String value;

    EmergencyType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static EmergencyType fromValue(String value) {
        for (EmergencyType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown emergency type: " + value);
    }
}
