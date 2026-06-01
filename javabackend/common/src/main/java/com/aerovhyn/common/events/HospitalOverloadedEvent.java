package com.aerovhyn.common.events;

import java.time.Instant;

public record HospitalOverloadedEvent(
        Long hospitalId,
        String hospitalName,
        int currentLoad,
        int maxCapacity,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "HOSPITAL_OVERLOADED";
    }
}
