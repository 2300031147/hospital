package com.aerovhyn.routing.service.impl;

import com.aerovhyn.common.dto.HospitalInfoDto;
import com.aerovhyn.common.dto.PatientVitalsDto;
import com.aerovhyn.common.dto.RankedHospitalDto;
import com.aerovhyn.common.dto.SeverityResultDto;
import com.aerovhyn.common.dto.SystemSettingsDto;
import com.aerovhyn.common.enums.SeverityLevel;
import com.aerovhyn.common.enums.AmbulanceStatus;
import com.aerovhyn.common.events.BedConflictResolvedEvent;
import com.aerovhyn.common.events.RerouteEvent;
import com.aerovhyn.core.service.BedConflictService;
import com.aerovhyn.core.service.BedReservationService;
import com.aerovhyn.domain.entity.AmbulanceEntity;
import com.aerovhyn.domain.entity.HospitalEntity;
import com.aerovhyn.domain.entity.LogEntity;
import com.aerovhyn.domain.repository.AmbulanceRepository;
import com.aerovhyn.domain.repository.HospitalRepository;
import com.aerovhyn.domain.repository.LogRepository;
import com.aerovhyn.routing.service.HospitalRanker;
import com.aerovhyn.routing.service.SystemSettingsService;
import com.aerovhyn.routing.service.SeverityClassifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BedConflictServiceImpl implements BedConflictService {

    private static final Logger log = LoggerFactory.getLogger(BedConflictServiceImpl.class);

    private final AmbulanceRepository ambulanceRepository;
    private final HospitalRepository hospitalRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final HospitalRanker hospitalRanker;
    private final SystemSettingsService settingsService;
    private final BedReservationService bedReservationService;
    private final SeverityClassifier severityClassifier;
    private final LogRepository logRepository;
    private final ObjectMapper objectMapper;

    public BedConflictServiceImpl(
            AmbulanceRepository ambulanceRepository,
            HospitalRepository hospitalRepository,
            ApplicationEventPublisher eventPublisher,
            HospitalRanker hospitalRanker,
            SystemSettingsService settingsService,
            BedReservationService bedReservationService,
            SeverityClassifier severityClassifier,
            LogRepository logRepository,
            ObjectMapper objectMapper) {
        this.ambulanceRepository = ambulanceRepository;
        this.hospitalRepository = hospitalRepository;
        this.eventPublisher = eventPublisher;
        this.hospitalRanker = hospitalRanker;
        this.settingsService = settingsService;
        this.bedReservationService = bedReservationService;
        this.severityClassifier = severityClassifier;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public boolean resolveConflict(Long hospitalId, Long newAmbulanceId, SeverityResultDto newSeverity, double newDistanceKm) {
        List<AmbulanceEntity> conflicting = ambulanceRepository.findByDestinationHospitalId(hospitalId).stream()
                .filter(a -> AmbulanceStatus.EN_ROUTE.getValue().equals(a.getStatus()) && !a.getId().equals(newAmbulanceId))
                .toList();

        if (conflicting.isEmpty()) return false;

        HospitalEntity targetHosp = hospitalRepository.findById(hospitalId).orElse(null);
        if (targetHosp == null) return false;

        SystemSettingsDto settings = settingsService.getSettings();
        double maxDist = settings.maxRoutingDistanceKm();
        double newPriority = newSeverity.score() + (1.0 - Math.min(newDistanceKm / maxDist, 1.0)) * 0.1;

        AmbulanceEntity weakestAmbulance = null;
        double lowestPriority = Double.MAX_VALUE;
        SeverityResultDto weakestSeverity = null;
        PatientVitalsDto weakestVitals = null;

        for (AmbulanceEntity conflict : conflicting) {
            try {
                PatientVitalsDto conflictVitals = objectMapper.readValue(conflict.getPatientVitals(), PatientVitalsDto.class);
                SeverityResultDto conflictSeverity = severityClassifier.classify(conflictVitals);
                double conflictDistance = haversineDistance(conflict.getLat(), conflict.getLon(), targetHosp.getLat(), targetHosp.getLon());
                double conflictPriority = conflictSeverity.score() + (1.0 - Math.min(conflictDistance / maxDist, 1.0)) * 0.1;

                if (conflictPriority < lowestPriority) {
                    lowestPriority = conflictPriority;
                    weakestAmbulance = conflict;
                    weakestSeverity = conflictSeverity;
                    weakestVitals = conflictVitals;
                }
            } catch (Exception e) {
                continue;
            }
        }

        if (weakestAmbulance == null) return false;

        if (newPriority <= lowestPriority) {
            // The new ambulance lost the conflict!
            return true;
        }

        // The existing weakest ambulance lost the conflict! Reroute it.
        List<HospitalEntity> allActiveEntities = hospitalRepository.findAllByStatus("active");
        List<HospitalInfoDto> allActiveHospitals = allActiveEntities.stream().map(h -> {
            List<String> specialists = List.of();
            if (h.getSpecialists() != null && !h.getSpecialists().isEmpty()) {
                try {
                    specialists = objectMapper.readValue(h.getSpecialists(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                } catch (Exception e) {}
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

        List<RankedHospitalDto> altRanked = hospitalRanker.rank(
                allActiveHospitals, weakestAmbulance.getLat(), weakestAmbulance.getLon(), weakestSeverity, weakestVitals.emergencyType(), settings);

        RankedHospitalDto alt = null;
        for (RankedHospitalDto potentialAlt : altRanked) {
            if (!potentialAlt.hospital().id().equals(hospitalId)) {
                if (weakestSeverity.level() == SeverityLevel.CRITICAL) {
                    boolean reserved = bedReservationService.softReserve(potentialAlt.hospital().id(), weakestAmbulance.getId());
                    if (reserved) {
                        bedReservationService.release(hospitalId, weakestAmbulance.getId());
                        alt = potentialAlt;
                        break;
                    }
                } else {
                    alt = potentialAlt;
                    break;
                }
            }
        }

        if (alt != null) {
            weakestAmbulance.setDestinationHospitalId(alt.hospital().id());
            weakestAmbulance.setEtaMinutes(alt.etaMinutes());
            ambulanceRepository.save(weakestAmbulance);

            eventPublisher.publishEvent(new BedConflictResolvedEvent(
                    weakestAmbulance.getId(), hospitalId, alt.hospital().id(), alt.hospital().name(),
                    "Higher priority patient incoming", lowestPriority, Instant.now()));

            eventPublisher.publishEvent(new RerouteEvent(
                    weakestAmbulance.getId(), hospitalId, alt.hospital().id(), alt.hospital().name(),
                    alt.hospital().lat(), alt.hospital().lon(), "conflict_resolution", Instant.now()));

            LogEntity logEntry = new LogEntity("conflict_resolved");
            logEntry.setAmbulanceId(weakestAmbulance.getId());
            logEntry.setHospitalSelectedId(alt.hospital().id());
            logEntry.setScore(alt.finalScore());
            logEntry.setDetails("Conflict at hospital #" + hospitalId + ", rerouted to " + alt.hospital().name());
            logEntry.setTimestamp(LocalDateTime.now());
            logRepository.save(logEntry);

            log.info("Conflict resolved: ambulance {} rerouted from hospital {} to {}",
                    weakestAmbulance.getId(), hospitalId, alt.hospital().id());
        }

        return false;
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0; // Earth radius
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return R * c;
    }
}
