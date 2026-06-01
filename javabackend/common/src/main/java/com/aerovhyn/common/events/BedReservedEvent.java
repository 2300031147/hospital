package com.aerovhyn.common.events;

import java.time.Instant;

public record BedReservedEvent(
        Long hospitalId,
        Long ambulanceId,
        String hospitalName,
        int icuBeds,
        int softReserve,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "BED_RESERVED";
    }
}
