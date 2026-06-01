package com.aerovhyn.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AmbulanceStatus {
    IDLE("idle"),
    EN_ROUTE("en_route"),
    ACCEPTED("accepted"),
    AT_SCENE("at_scene"),
    TRANSPORTING("transporting"),
    COMPLETED("completed");

    private final String value;

    AmbulanceStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
