package com.aerovhyn.common.events;

import java.time.Instant;

public record HandoffCompletedEvent(
        Long ambulanceId,
        Long hospitalId,
        String hospitalName,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "HANDOFF_COMPLETED";
    }
}
