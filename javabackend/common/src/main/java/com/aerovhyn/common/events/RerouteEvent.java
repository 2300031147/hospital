package com.aerovhyn.common.events;

import java.time.Instant;

public record RerouteEvent(
        Long ambulanceId,
        Long oldHospitalId,
        Long newHospitalId,
        String newHospitalName,
        double newHospitalLat,
        double newHospitalLon,
        String reason,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "REROUTE";
    }
}
