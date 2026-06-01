package com.aerovhyn.realtime.listener;

import com.aerovhyn.common.dto.HospitalInfoDto;
import com.aerovhyn.common.events.*;
import com.aerovhyn.core.service.HospitalService;
import com.aerovhyn.realtime.handler.WebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);
    private final WebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final HospitalService hospitalService;

    public WebSocketEventListener(WebSocketHandler webSocketHandler, ObjectMapper objectMapper, HospitalService hospitalService) {
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
        this.hospitalService = hospitalService;
    }

    private void send(Map<String, Object> message) {
        try {
            webSocketHandler.broadcast(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            log.error("WS broadcast error: {}", e.getMessage());
        }
    }

    @Async
    @EventListener
    public void handleAmbulanceDispatched(AmbulanceDispatchedEvent event) {
        send(Map.of(
                "type", "ambulance_routed",
                "ambulance_id", event.ambulanceId(),
                "hospital_id", event.hospitalId(),
                "hospital_name", event.hospitalName(),
                "severity", Map.of(
                        "level", event.severity(),
                        "score", event.severityScore(),
                        "reasons", List.of()
                ),
                "eta_minutes", event.etaMinutes(),
                "score", event.finalScore()
        ));
    }

    @Async
    @EventListener
    public void handleHandoffAcknowledged(com.aerovhyn.common.events.HandoffAcknowledgedEvent event) {
        send(Map.of(
                "type", "handoff_acknowledged",
                "hospital_id", event.hospitalId(),
                "hospital_name", event.hospitalName(),
                "message", event.hospitalName() + " has acknowledged incoming patient"
        ));
    }

    @Async
    @EventListener
    public void handleHospitalsUpdated(HospitalsUpdatedEvent event) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", "hospital_update");
        payload.put("hospital_id", event.hospitalId());
        payload.put("name", event.name());
        payload.put("icu_beds", event.icuBeds());
        payload.put("ventilators", event.ventilators());
        payload.put("current_load", event.currentLoad());
        payload.put("soft_reserve", event.softReserve());
        payload.put("status", event.status());

        try {
            HospitalInfoDto info = hospitalService.getById(event.hospitalId());
            payload.put("hospital", info);
        } catch (Exception e) {
            Map<String, Object> fallback = new java.util.HashMap<>();
            fallback.put("id", event.hospitalId());
            fallback.put("name", event.name());
            fallback.put("icu_beds", event.icuBeds());
            fallback.put("ventilators", event.ventilators());
            fallback.put("current_load", event.currentLoad());
            fallback.put("soft_reserve", event.softReserve());
            fallback.put("status", event.status());
            fallback.put("lat", 0.0);
            fallback.put("lon", 0.0);
            fallback.put("specialists", List.of());
            payload.put("hospital", fallback);
        }

        send(payload);
    }

    @Async
    @EventListener
    public void handleAmbulancePosition(AmbulancePositionUpdatedEvent event) {
        send(Map.of(
                "type", "location_update",
                "ambulance_id", event.ambulanceId(),
                "lat", event.lat(),
                "lon", event.lon()
        ));
    }

    @Async
    @EventListener
    public void handlePatientAccepted(PatientAcceptedEvent event) {
        send(Map.of(
                "type", "patient_accepted",
                "hospital_id", event.hospitalId(),
                "hospital_name", event.hospitalName(),
                "ambulance_id", event.ambulanceId(),
                "message", event.hospitalName() + " has accepted and locked bed for patient #" + event.ambulanceId()
        ));
    }

    @Async
    @EventListener
    public void handleBedReleased(BedReleasedEvent event) {
        send(Map.of(
                "type", "bed_released",
                "hospital_id", event.hospitalId(),
                "hospital_name", event.hospitalName(),
                "icu_beds", event.icuBeds(),
                "soft_reserve", event.softReserve()
        ));
    }

    @Async
    @EventListener
    public void handleReroute(RerouteEvent event) {
        send(Map.of(
                "type", "reroute",
                "ambulance_id", event.ambulanceId(),
                "old_hospital_id", event.oldHospitalId(),
                "to_hospital", event.newHospitalId(),
                "to_hospital_name", event.newHospitalName(),
                "to_hospital_lat", event.newHospitalLat(),
                "to_hospital_lon", event.newHospitalLon(),
                "reason", event.reason()
        ));
    }

    @Async
    @EventListener
    public void handleBedConflictResolved(BedConflictResolvedEvent event) {
        send(Map.of(
                "type", "bed_conflict_resolved",
                "ambulance_id", event.ambulanceId(),
                "original_hospital_id", event.originalHospitalId(),
                "resolved_hospital_id", event.resolvedHospitalId(),
                "resolved_hospital_name", event.resolvedHospitalName(),
                "reason", event.reason()
        ));
    }

    @Async
    @EventListener
    public void handleHandoffAlert(HandoffAlertEvent event) {
        Map<String, Object> handoff = Map.of(
                "ambulance_id", event.ambulanceId(),
                "hospital_id", event.hospitalId(),
                "hospital_name", event.hospitalName(),
                "severity", event.severity(),
                "vitals", event.vitals(),
                "eta_minutes", event.etaMinutes(),
                "prep_instructions", event.prepInstructions(),
                "bed_reserved", event.bedReserved()
        );
        send(Map.of("type", "handoff_alert", "handoff", handoff));
    }

    @Async
    @EventListener
    public void handleHospitalOverloaded(HospitalOverloadedEvent event) {
        send(Map.of(
                "type", "hospital_overloaded",
                "hospital", Map.of(
                        "id", event.hospitalId(),
                        "name", event.hospitalName(),
                        "current_load", event.currentLoad(),
                        "max_capacity", event.maxCapacity(),
                        "status", "diverted"
                )
        ));
    }

    @Async
    @EventListener
    public void handleAlert(AlertEvent event) {
        send(Map.of(
                "type", "alert",
                "message", event.message(),
                "level", event.level()
        ));
    }

    @Async
    @EventListener
    public void handleHandoffCompleted(HandoffCompletedEvent event) {
        send(Map.of(
                "type", "handoff_completed",
                "ambulance_id", event.ambulanceId(),
                "hospital_id", event.hospitalId(),
                "hospital_name", event.hospitalName()
        ));
    }

    @Async
    @EventListener
    public void handleBedReserved(BedReservedEvent event) {
        send(Map.of(
                "type", "bed_reserved",
                "hospital_id", event.hospitalId(),
                "ambulance_id", event.ambulanceId(),
                "hospital_name", event.hospitalName(),
                "icu_beds", event.icuBeds(),
                "soft_reserve", event.softReserve()
        ));
    }
}
