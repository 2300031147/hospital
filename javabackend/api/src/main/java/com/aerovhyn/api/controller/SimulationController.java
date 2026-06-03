package com.aerovhyn.api.controller;

import com.aerovhyn.common.events.AlertEvent;
import com.aerovhyn.common.events.HospitalOverloadedEvent;
import com.aerovhyn.common.events.RerouteEvent;
import com.aerovhyn.common.dto.HospitalInfoDto;
import com.aerovhyn.common.dto.PatientVitalsDto;
import com.aerovhyn.common.dto.SeverityResultDto;
import com.aerovhyn.common.dto.SystemSettingsDto;
import com.aerovhyn.common.dto.RankedHospitalDto;
import com.aerovhyn.common.enums.SeverityLevel;
import com.aerovhyn.common.enums.AmbulanceStatus;
import com.aerovhyn.core.service.HospitalService;
import com.aerovhyn.core.service.BedReservationService;
import com.aerovhyn.routing.service.HospitalRanker;
import com.aerovhyn.routing.service.SystemSettingsService;
import com.aerovhyn.routing.service.SeverityClassifier;
import com.aerovhyn.domain.entity.HospitalEntity;
import com.aerovhyn.domain.entity.AmbulanceEntity;
import com.aerovhyn.domain.entity.LogEntity;
import com.aerovhyn.domain.repository.AmbulanceRepository;
import com.aerovhyn.domain.repository.BlockchainRepository;
import com.aerovhyn.domain.repository.HospitalRepository;
import com.aerovhyn.domain.repository.LogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulate")
@PreAuthorize("hasRole('COMMAND_CENTER')")
public class SimulationController {

    @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private final HospitalService hospitalService;
    private final HospitalRepository hospitalRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AmbulanceRepository ambulanceRepository;
    private final LogRepository logRepository;
    private final BlockchainRepository blockchainRepository;
    private final HospitalRanker hospitalRanker;
    private final SystemSettingsService settingsService;
    private final BedReservationService bedReservationService;
    private final SeverityClassifier severityClassifier;
    private final ObjectMapper objectMapper;
    private final com.aerovhyn.api.service.DatabaseSeederService databaseSeederService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final com.aerovhyn.api.config.RateLimitFilter rateLimitFilter;

    public SimulationController(
            HospitalService hospitalService,
            HospitalRepository hospitalRepository,
            ApplicationEventPublisher eventPublisher,
            AmbulanceRepository ambulanceRepository,
            LogRepository logRepository,
            BlockchainRepository blockchainRepository,
            HospitalRanker hospitalRanker,
            SystemSettingsService settingsService,
            BedReservationService bedReservationService,
            SeverityClassifier severityClassifier,
            ObjectMapper objectMapper,
            com.aerovhyn.api.service.DatabaseSeederService databaseSeederService,
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
            com.aerovhyn.api.config.RateLimitFilter rateLimitFilter) {
        this.hospitalService = hospitalService;
        this.hospitalRepository = hospitalRepository;
        this.eventPublisher = eventPublisher;
        this.ambulanceRepository = ambulanceRepository;
        this.logRepository = logRepository;
        this.blockchainRepository = blockchainRepository;
        this.hospitalRanker = hospitalRanker;
        this.settingsService = settingsService;
        this.bedReservationService = bedReservationService;
        this.severityClassifier = severityClassifier;
        this.objectMapper = objectMapper;
        this.databaseSeederService = databaseSeederService;
        this.redisTemplate = redisTemplate;
        this.rateLimitFilter = rateLimitFilter;
    }

    @PostMapping("/overload/{hospitalId}")
    public Map<String, String> simulateOverload(@PathVariable Long hospitalId) {
        if (activeProfile != null && activeProfile.contains("prod")) {
            throw new com.aerovhyn.common.exception.AerovhynException("Simulation disabled in production", 403);
        }

        var hospital = hospitalService.getById(hospitalId);
        int maxCapacity = hospital.maxCapacity() > 0 ? hospital.maxCapacity() : 100;
        int overloaded = (int) (maxCapacity * 0.98);

        var update = new com.aerovhyn.common.dto.HospitalUpdateDto(
                0, null, 0, null, null, overloaded, null, null, null);
        hospitalService.update(hospitalId, update);

        var entity = hospitalRepository.findById(hospitalId).orElse(null);
        if (entity != null) {
            entity.setSoftReserve(0);
            entity = hospitalRepository.save(entity);
        }

        eventPublisher.publishEvent(new HospitalOverloadedEvent(
                hospitalId, hospital.name(), overloaded,
                maxCapacity, Instant.now()));
        eventPublisher.publishEvent(new AlertEvent(
                "Hospital " + hospital.name() + " has reached critical capacity",
                "warning", Instant.now()));

        if (entity != null) {
            checkAndReroute(hospitalId, entity);
        }

        eventPublisher.publishEvent(new com.aerovhyn.common.events.BlockchainAuditEvent(
                "Simulation overload triggered for " + hospital.name(), Instant.now()));

        return Map.of("status", "overloaded", "hospital", hospital.name());
    }

    private void checkAndReroute(Long hospitalId, HospitalEntity overloadedHospital) {
        List<AmbulanceEntity> affected = ambulanceRepository.findAll().stream()
                .filter(amb -> AmbulanceStatus.EN_ROUTE.getValue().equals(amb.getStatus()) && hospitalId.equals(amb.getDestinationHospitalId()))
                .toList();

        if (affected.isEmpty()) {
            return;
        }

        List<HospitalEntity> altEntities = hospitalRepository.findAllByStatus("active").stream()
                .filter(h -> !h.getId().equals(hospitalId))
                .toList();

        List<HospitalInfoDto> altHospitals = altEntities.stream().map(h -> {
            List<String> specialists = List.of();
            if (h.getSpecialists() != null && !h.getSpecialists().isEmpty()) {
                try {
                    specialists = objectMapper.readValue(h.getSpecialists(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                } catch (Exception e) {
                    // ignore
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

        for (AmbulanceEntity amb : affected) {
            try {
                PatientVitalsDto vitals = objectMapper.readValue(amb.getPatientVitals(), PatientVitalsDto.class);
                SeverityResultDto severity = severityClassifier.classify(vitals);

                List<RankedHospitalDto> ranked = hospitalRanker.rank(
                        altHospitals, amb.getLat(), amb.getLon(), severity, vitals.emergencyType(), settings);

                if (ranked.isEmpty()) {
                    continue;
                }

                RankedHospitalDto newBest = ranked.get(0);

                boolean bedReserved = false;
                if (severity.level() == SeverityLevel.CRITICAL) {
                    bedReserved = bedReservationService.softReserve(newBest.hospital().id(), amb.getId());
                }

                amb.setDestinationHospitalId(newBest.hospital().id());
                amb.setEtaMinutes(newBest.etaMinutes());
                ambulanceRepository.save(amb);

                LogEntity logEntry = new LogEntity("ambulance_rerouted");
                logEntry.setAmbulanceId(amb.getId());
                logEntry.setHospitalSelectedId(newBest.hospital().id());
                logEntry.setScore(newBest.finalScore());
                logEntry.setDetails("Hospital #" + hospitalId + " overloaded. Rerouted to " + newBest.hospital().name()
                        + " (" + newBest.distanceKm() + "km, ETA: " + newBest.etaMinutes() + "min)");
                logEntry.setTimestamp(LocalDateTime.now());
                logRepository.save(logEntry);

                // Publish RerouteEvent to trigger real-time and blockchain audit logging
                eventPublisher.publishEvent(new RerouteEvent(
                        amb.getId(), hospitalId, newBest.hospital().id(), newBest.hospital().name(),
                        newBest.hospital().lat(), newBest.hospital().lon(), "hospital_overloaded", Instant.now()));

            } catch (Exception e) {
                // ignore
            }
        }
    }

    @PostMapping("/reset")
    public Map<String, String> reset() {
        if (activeProfile != null && activeProfile.contains("prod")) {
            throw new com.aerovhyn.common.exception.AerovhynException("Simulation disabled in production", 403);
        }

        databaseSeederService.wipeAndReseed();
        invalidateCache();
        rateLimitFilter.clearBuckets();

        eventPublisher.publishEvent(new AlertEvent(
                "System data has been reset and seeded to default state.", "info", Instant.now()));
        eventPublisher.publishEvent(new com.aerovhyn.common.events.BlockchainAuditEvent(
                "Simulation reset triggered", Instant.now()));

        return Map.of("status", "reset");
    }

    private void invalidateCache() {
        try {
            java.util.Set<String> keys = redisTemplate.keys("hospitals:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {}
    }
}
