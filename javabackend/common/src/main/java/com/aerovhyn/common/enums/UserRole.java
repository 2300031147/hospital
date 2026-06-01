package com.aerovhyn.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum UserRole {
    PARAMEDIC("paramedic"),
    HOSPITAL_ADMIN("hospital_admin"),
    COMMAND_CENTER("command_center"),
    DISPATCHER("dispatcher");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
