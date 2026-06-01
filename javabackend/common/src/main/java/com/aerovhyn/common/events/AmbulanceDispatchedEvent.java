package com.aerovhyn.common.events;

import java.time.Instant;

public record AmbulanceDispatchedEvent(
        Long ambulanceId,
        Long hospitalId,
        String hospitalName,
        String severity,
        double severityScore,
        double finalScore,
        double distanceKm,
        double etaMinutes,
        boolean bedReserved,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "AMBULANCE_DISPATCHED";
    }
}
