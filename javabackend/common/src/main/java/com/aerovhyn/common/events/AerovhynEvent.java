package com.aerovhyn.common.events;

import java.time.Instant;

public interface AerovhynEvent {
    String getEventType();
    Instant getTimestamp();
}
