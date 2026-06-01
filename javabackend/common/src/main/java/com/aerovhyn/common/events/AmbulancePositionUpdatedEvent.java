package com.aerovhyn.common.events;

import java.time.Instant;

public record AmbulancePositionUpdatedEvent(
        Long ambulanceId,
        double lat,
        double lon,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "AMBULANCE_POSITION_UPDATED";
    }
}
