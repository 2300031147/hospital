package com.aerovhyn.analytics.listener;

import com.aerovhyn.analytics.service.BlockchainAuditService;
import com.aerovhyn.common.events.AmbulanceDispatchedEvent;
import com.aerovhyn.common.events.BedConflictResolvedEvent;
import com.aerovhyn.common.events.BedReservedEvent;
import com.aerovhyn.common.events.HandoffCompletedEvent;
import com.aerovhyn.common.events.HospitalOverloadedEvent;
import com.aerovhyn.common.events.RerouteEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditLogEventListener {

    private final BlockchainAuditService blockchainAuditService;

    public AuditLogEventListener(BlockchainAuditService blockchainAuditService) {
        this.blockchainAuditService = blockchainAuditService;
    }

    @Async
    @EventListener
    public void handleAmbulanceDispatched(AmbulanceDispatchedEvent event) {
        blockchainAuditService.addBlock(Map.of(
                "event", "ROUTING_DECISION",
                "ambulance_id", event.ambulanceId(),
                "severity", event.severity(),
                "hospital_id", event.hospitalId(),
                "final_score", event.finalScore()
        ));
    }

    @Async
    @EventListener
    public void handleBedReserved(BedReservedEvent event) {
        blockchainAuditService.addBlock(Map.of(
                "event", "BED_RESERVED",
                "hospital_id", event.hospitalId(),
                "ambulance_id", event.ambulanceId()
        ));
    }

    @Async
    @EventListener
    public void handleHandoffCompleted(HandoffCompletedEvent event) {
        blockchainAuditService.addBlock(Map.of(
                "event", "HANDOFF_COMPLETED",
                "ambulance_id", event.ambulanceId(),
                "hospital_id", event.hospitalId()
        ));
    }

    @Async
    @EventListener
    public void handleBedConflictResolved(BedConflictResolvedEvent event) {
        blockchainAuditService.addBlock(Map.of(
                "event", "BED_CONFLICT_RESOLVED",
                "ambulance_id", event.ambulanceId(),
                "original_hospital_id", event.originalHospitalId(),
                "resolved_hospital_id", event.resolvedHospitalId(),
                "reason", event.reason()
        ));
    }

    @Async
    @EventListener
    public void handleHospitalOverloaded(HospitalOverloadedEvent event) {
        blockchainAuditService.addBlock(Map.of(
                "event", "HOSPITAL_OVERLOADED",
                "hospital_id", event.hospitalId(),
                "hospital_name", event.hospitalName(),
                "current_load", event.currentLoad(),
                "max_capacity", event.maxCapacity()
        ));
    }

    @Async
    @EventListener
    public void handleReroute(RerouteEvent event) {
        blockchainAuditService.addBlock(Map.of(
                "event", "AMBULANCE_REROUTED",
                "ambulance_id", event.ambulanceId(),
                "from_hospital", event.oldHospitalId(),
                "to_hospital", event.newHospitalId(),
                "to_hospital_name", event.newHospitalName(),
                "reason", event.reason()
        ));
    }
}
