package com.aerovhyn.routing.service.impl;

import com.aerovhyn.common.dto.HospitalInfoDto;
import com.aerovhyn.common.dto.PatientVitalsDto;
import com.aerovhyn.common.dto.RankedHospitalDto;
import com.aerovhyn.common.dto.SeverityResultDto;
import com.aerovhyn.common.dto.SystemSettingsDto;
import com.aerovhyn.common.enums.SeverityLevel;
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
                .filter(a -> "en_route".equals(a.getStatus()) && !a.getId().equals(newAmbulanceId))
                .toList();

        if (conflicting.isEmpty()) return false;

        SystemSettingsDto settings = settingsService.getSettings();
        double maxDist = settings.maxRoutingDistanceKm();

        double newPriority = newSeverity.score() + (1.0 - Math.min(newDistanceKm / maxDist, 1.0)) * 0.1;

        List<HospitalEntity> allActiveEntities = hospitalRepository.findAllByStatus("active");
        List<HospitalInfoDto> allActiveHospitals = allActiveEntities.stream().map(h -> {
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

        for (AmbulanceEntity conflict : conflicting) {
            PatientVitalsDto conflictVitals;
            try {
                conflictVitals = objectMapper.readValue(conflict.getPatientVitals(), PatientVitalsDto.class);
            } catch (Exception e) {
                continue;
            }

            SeverityResultDto conflictSeverity = severityClassifier.classify(conflictVitals);

            HospitalEntity targetHosp = allActiveEntities.stream()
                    .filter(h -> h.getId().equals(hospitalId))
                    .findFirst().orElse(null);

            double conflictPriority;
            if (targetHosp != null) {
                double conflictDistance = haversineDistance(conflict.getLat(), conflict.getLon(), targetHosp.getLat(), targetHosp.getLon());
                conflictPriority = conflictSeverity.score() + (1.0 - Math.min(conflictDistance / maxDist, 1.0)) * 0.1;
            } else {
                conflictPriority = conflictSeverity.score();
            }

            if (newPriority < conflictPriority) {
                // The new ambulance lost the conflict!
                return true;
            }

            // The existing ambulance lost the conflict! Reroute it.
            List<RankedHospitalDto> altRanked = hospitalRanker.rank(
                    allActiveHospitals, conflict.getLat(), conflict.getLon(), conflictSeverity, conflictVitals.emergencyType(), settings);

            RankedHospitalDto alt = null;
            for (RankedHospitalDto potentialAlt : altRanked) {
                if (!potentialAlt.hospital().id().equals(hospitalId)) {
                    if (conflictSeverity.level() == SeverityLevel.CRITICAL) {
                        boolean reserved = bedReservationService.softReserve(potentialAlt.hospital().id(), conflict.getId());
                        if (reserved) {
                            bedReservationService.release(hospitalId, conflict.getId());
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
                conflict.setDestinationHospitalId(alt.hospital().id());
                conflict.setEtaMinutes(alt.etaMinutes());
                ambulanceRepository.save(conflict);

                // Publish conflict resolution events
                eventPublisher.publishEvent(new BedConflictResolvedEvent(
                        conflict.getId(), hospitalId, alt.hospital().id(), alt.hospital().name(),
                        "Higher priority patient incoming", conflictPriority, Instant.now()));

                eventPublisher.publishEvent(new RerouteEvent(
                        conflict.getId(), hospitalId, alt.hospital().id(), alt.hospital().name(),
                        alt.hospital().lat(), alt.hospital().lon(), "conflict_resolution", Instant.now()));

                LogEntity logEntry = new LogEntity("conflict_resolved");
                logEntry.setAmbulanceId(conflict.getId());
                logEntry.setHospitalSelectedId(alt.hospital().id());
                logEntry.setScore(alt.finalScore());
                logEntry.setDetails("Conflict at hospital #" + hospitalId + ", rerouted to " + alt.hospital().name());
                logEntry.setTimestamp(LocalDateTime.now());
                logRepository.save(logEntry);

                log.info("Conflict resolved: ambulance {} rerouted from hospital {} to {}",
                        conflict.getId(), hospitalId, alt.hospital().id());
            }
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
