package com.aerovhyn.common.events;

import java.time.Instant;

public record BedReleasedEvent(
        Long hospitalId,
        String hospitalName,
        int icuBeds,
        int softReserve,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "BED_RELEASED";
    }
}
