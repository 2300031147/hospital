package com.aerovhyn.common.events;

import java.time.Instant;

public record AlertEvent(
        String message,
        String level,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "ALERT";
    }
}
