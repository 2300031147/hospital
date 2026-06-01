package com.aerovhyn.common.events;

import java.time.Instant;

public record HospitalsUpdatedEvent(
        Long hospitalId,
        String name,
        int icuBeds,
        int ventilators,
        int currentLoad,
        int softReserve,
        String status,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "HOSPITALS_UPDATED";
    }
}
