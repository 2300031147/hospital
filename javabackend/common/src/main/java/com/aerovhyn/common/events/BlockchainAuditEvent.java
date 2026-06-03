package com.aerovhyn.common.events;

import java.time.Instant;

public record BlockchainAuditEvent(
        String details,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "BLOCKCHAIN_AUDIT";
    }

    @Override
    public Instant timestamp() {
        return timestamp;
    }
}
