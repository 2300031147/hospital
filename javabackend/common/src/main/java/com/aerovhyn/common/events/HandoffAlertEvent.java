package com.aerovhyn.common.events;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record HandoffAlertEvent(
        Long ambulanceId,
        Long hospitalId,
        String hospitalName,
        Map<String, Object> severity,
        Map<String, Object> vitals,
        double etaMinutes,
        List<String> prepInstructions,
        boolean bedReserved,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "HANDOFF_ALERT";
    }
}
