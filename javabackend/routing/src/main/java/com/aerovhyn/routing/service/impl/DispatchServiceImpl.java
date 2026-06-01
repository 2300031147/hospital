package com.aerovhyn.routing.service.impl;

import com.aerovhyn.common.dto.*;
import com.aerovhyn.common.events.AmbulanceDispatchedEvent;
import com.aerovhyn.common.events.CriticalAlertEvent;
import com.aerovhyn.common.events.HandoffAlertEvent;
import com.aerovhyn.common.enums.EmergencyType;
import com.aerovhyn.common.enums.SeverityLevel;
import com.aerovhyn.common.exception.ResourceNotFoundException;
import com.aerovhyn.core.service.AmbulanceService;
import com.aerovhyn.core.service.BedReservationService;
import com.aerovhyn.core.service.BedConflictService;
import com.aerovhyn.domain.entity.HospitalEntity;
import com.aerovhyn.domain.entity.LogEntity;
import com.aerovhyn.domain.repository.HospitalRepository;
import com.aerovhyn.domain.repository.LogRepository;
import com.aerovhyn.routing.service.DispatchService;
import com.aerovhyn.routing.service.HospitalRanker;
import com.aerovhyn.routing.service.OsrmClient;
import com.aerovhyn.routing.service.SeverityClassifier;
import com.aerovhyn.routing.service.SystemSettingsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DispatchServiceImpl implements DispatchService {

    private final SeverityClassifier severityClassifier;
    private final HospitalRanker hospitalRanker;
    private final SystemSettingsService settingsService;
    private final HospitalRepository hospitalRepository;
    private final AmbulanceService ambulanceService;
    private final BedReservationService bedReservationService;
    private final BedConflictService bedConflictService;
    private final LogRepository logRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(DispatchServiceImpl.class);

    public DispatchServiceImpl(
            SeverityClassifier severityClassifier,
            HospitalRanker hospitalRanker,
            SystemSettingsService settingsService,
            HospitalRepository hospitalRepository,
            AmbulanceService ambulanceService,
            BedReservationService bedReservationService,
            BedConflictService bedConflictService,
            LogRepository logRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.severityClassifier = severityClassifier;
        this.hospitalRanker = hospitalRanker;
        this.settingsService = settingsService;
        this.hospitalRepository = hospitalRepository;
        this.ambulanceService = ambulanceService;
        this.bedReservationService = bedReservationService;
        this.bedConflictService = bedConflictService;
        this.logRepository = logRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public RouteResponseDto routeAmbulance(RouteRequestDto request, Long ambulanceId) {
        if (ambulanceId == null) {
            throw new com.aerovhyn.common.exception.ValidationException("Paramedic token missing ambulance_id");
        }
        SeverityResultDto severity = severityClassifier.classify(request.vitals());

        List<HospitalEntity> entities = hospitalRepository.findAllByStatus("active");
        List<HospitalInfoDto> hospitals = entities.stream().map(h -> {
            List<String> specialists = List.of();
            if (h.getSpecialists() != null && !h.getSpecialists().isEmpty()) {
                try {
                    specialists = objectMapper.readValue(h.getSpecialists(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                } catch (Exception e) {
                    log.warn("Failed to parse specialists for hospital {}: {}", h.getId(), e.getMessage());
                }
            }
            return new HospitalInfoDto(
                    h.getId(), h.getName(), h.getLat(), h.getLon(),
                    h.getIcuBeds(), h.getTotalIcuBeds(), h.getSoftReserve(),
                    h.getVentilators(), h.getTotalVentilators(),
                    specialists, h.getCurrentLoad(), h.getMaxCapacity(),
                    h.getEquipmentScore(), h.getStatus(),
                    h.getLastUpdated() != null ? h.getLastUpdated().toString() : null
            );
        }).toList();

        SystemSettingsDto settings = settingsService.getSettings();
        List<RankedHospitalDto> ranked = hospitalRanker.rank(
                hospitals, request.ambulanceLat(), request.ambulanceLon(), severity, request.vitals().emergencyType(), settings);

        if (ranked.isEmpty()) {
            throw new ResourceNotFoundException("No available hospitals");
        }

        RankedHospitalDto best = ranked.get(0);

        boolean rerouteNew = bedConflictService.resolveConflict(best.hospital().id(), ambulanceId, severity, best.distanceKm());
        if (rerouteNew) {
            if (ranked.size() > 1) {
                best = ranked.get(1);
            } else {
                log.warn("Conflict for ambulance #{} but no alternative hospital available", ambulanceId);
            }
        }

        boolean bedReserved = false;
        if (severity.level() == SeverityLevel.CRITICAL) {
            bedReserved = bedReservationService.softReserve(best.hospital().id(), ambulanceId);
        }

        LogEntity logEntry = new LogEntity("ambulance_routed");
        logEntry.setAmbulanceId(ambulanceId);
        logEntry.setHospitalSelectedId(best.hospital().id());
        logEntry.setScore(best.finalScore());
        logEntry.setDetails("Severity: " + severity.level() + " (" + severity.score()
                + "), Dest: " + best.hospital().name());
        logEntry.setTimestamp(LocalDateTime.now());
        logRepository.save(logEntry);

        eventPublisher.publishEvent(new AmbulanceDispatchedEvent(
                ambulanceId, best.hospital().id(), best.hospital().name(),
                severity.level().getValue(), severity.score(), best.finalScore(),
                best.distanceKm(), best.etaMinutes(), bedReserved, Instant.now()));

        List<String> prepInstructions = getPrepInstructions(severity.level(), request.vitals().emergencyType());
        eventPublisher.publishEvent(new HandoffAlertEvent(
                ambulanceId, best.hospital().id(), best.hospital().name(),
                Map.of("level", severity.level().getValue(), "score", severity.score(), "reasons", severity.reasons()),
                Map.of("heart_rate", request.vitals().heartRate(), "spo2", request.vitals().spo2(),
                        "systolic_bp", request.vitals().systolicBp(),
                        "emergency_type", request.vitals().emergencyType().getValue(),
                        "age", request.vitals().age()),
                best.etaMinutes(), prepInstructions, bedReserved, Instant.now()));

        if (severity.level() == SeverityLevel.CRITICAL) {
            eventPublisher.publishEvent(new CriticalAlertEvent(
                    best.hospital().id(), best.hospital().name(),
                    severity.level().getValue(), best.etaMinutes(), Instant.now()));
        }

        try {
            String vitalsJson = objectMapper.writeValueAsString(request.vitals());
            ambulanceService.updateStatus(ambulanceId, "en_route", best.hospital().id(),
                    severity.level().getValue(), request.vitals().emergencyType().getValue(),
                    vitalsJson, best.etaMinutes());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize vitals for status update: {}", e.getMessage());
            ambulanceService.updateStatus(ambulanceId, "en_route", best.hospital().id(),
                    severity.level().getValue(), request.vitals().emergencyType().getValue(),
                    "{}", best.etaMinutes());
        }

        return new RouteResponseDto(ambulanceId, severity, ranked, best);
    }

    @Override
    public SeverityResultDto classify(PatientVitalsDto vitals) {
        return severityClassifier.classify(vitals);
    }

    private List<String> getPrepInstructions(SeverityLevel level, EmergencyType emergencyType) {
        List<String> instructions = new ArrayList<>();

        if (level == SeverityLevel.CRITICAL) {
            instructions.add("⚠️ CRITICAL PATIENT — Prepare crash cart");
            instructions.add("Alert senior attending physician");
        }

        switch (emergencyType) {
            case CARDIAC -> instructions.addAll(List.of(
                    "Prepare ECG/defibrillator",
                    "Ready catheterization lab if available",
                    "Prepare IV nitroglycerin and aspirin"
            ));
            case TRAUMA -> instructions.addAll(List.of(
                    "Prepare trauma bay",
                    "Ready blood bank (O-negative on standby)",
                    "Alert surgical team"
            ));
            case RESPIRATORY -> instructions.addAll(List.of(
                    "Prepare ventilator",
                    "Ready intubation kit",
                    "Prepare bronchodilator nebulization"
            ));
            case NEUROLOGICAL -> instructions.addAll(List.of(
                    "Prepare CT scanner",
                    "Ready tPA for stroke if applicable",
                    "Alert neurology team"
            ));
            case FRACTURE -> instructions.addAll(List.of(
                    "Prepare X-ray / imaging",
                    "Ready splinting materials",
                    "Alert orthopedics team"
            ));
            case BURN -> instructions.addAll(List.of(
                    "Prepare burn care supplies",
                    "Ready IV fluids (Ringer's lactate)",
                    "Alert burn unit if available"
            ));
            default -> instructions.add("Prepare standard triage assessment");
        }

        if (level == SeverityLevel.CRITICAL) {
            instructions.add("System auto-locked 1 ICU bed for this arrival.");
        }

        return instructions;
    }
}
