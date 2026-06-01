package com.aerovhyn.common.events;

import java.time.Instant;

public record PatientAcceptedEvent(
        Long hospitalId,
        String hospitalName,
        Long ambulanceId,
        Instant timestamp
) implements AerovhynEvent {
    @Override
    public String getEventType() {
        return "PATIENT_ACCEPTED";
    }
}
