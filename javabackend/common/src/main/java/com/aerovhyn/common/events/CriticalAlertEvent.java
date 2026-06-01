package com.aerovhyn.common.events;

import java.time.Instant;

public record CriticalAlertEvent(
        Long hospitalId,
        String hospitalName,
        String patientSeverity,
        double etaMinutes,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "CRITICAL_ALERT";
    }
}
