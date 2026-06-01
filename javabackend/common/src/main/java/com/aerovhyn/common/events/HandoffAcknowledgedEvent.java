package com.aerovhyn.common.events;

import java.time.Instant;

public record HandoffAcknowledgedEvent(
        Long hospitalId,
        String hospitalName,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "HANDOFF_ACKNOWLEDGED";
    }
}
