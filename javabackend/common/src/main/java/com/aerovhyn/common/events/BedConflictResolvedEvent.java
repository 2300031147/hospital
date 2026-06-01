package com.aerovhyn.common.events;

import java.time.Instant;

/**
 * Published when a multi-ambulance bed conflict is resolved.
 * The core module publishes this after rerouting the lower-priority ambulance.
 * Analytics listens to append to the blockchain audit log.
 * Realtime listens to broadcast the reroute to WebSocket clients.
 */
public record BedConflictResolvedEvent(
        Long ambulanceId,
        Long originalHospitalId,
        Long resolvedHospitalId,
        String resolvedHospitalName,
        String reason,
        double newAmbulanceScore,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "BED_CONFLICT_RESOLVED";
    }
}
