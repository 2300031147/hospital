package com.aerovhyn.core.service.impl;

import com.aerovhyn.common.events.BedReleasedEvent;
import com.aerovhyn.common.events.BedReservedEvent;
import com.aerovhyn.common.events.AlertEvent;
import com.aerovhyn.core.service.BedReservationService;
import com.aerovhyn.domain.entity.HospitalEntity;
import com.aerovhyn.domain.entity.AmbulanceEntity;
import com.aerovhyn.domain.entity.LogEntity;
import com.aerovhyn.domain.repository.HospitalRepository;
import com.aerovhyn.domain.repository.AmbulanceRepository;
import com.aerovhyn.domain.repository.LogRepository;
import com.aerovhyn.common.enums.AmbulanceStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class BedReservationServiceImpl implements BedReservationService {

    private final StringRedisTemplate redisTemplate;
    private final HospitalRepository hospitalRepository;
    private final AmbulanceRepository ambulanceRepository;
    private final LogRepository logRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final long RESERVATION_TTL_MINUTES = 10;
    private static final String RESERVATION_KEY_PREFIX = "reservation:";

    public BedReservationServiceImpl(
            StringRedisTemplate redisTemplate,
            HospitalRepository hospitalRepository,
            AmbulanceRepository ambulanceRepository,
            LogRepository logRepository,
            ApplicationEventPublisher eventPublisher) {
        this.redisTemplate = redisTemplate;
        this.hospitalRepository = hospitalRepository;
        this.ambulanceRepository = ambulanceRepository;
        this.logRepository = logRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public boolean softReserve(Long hospitalId, Long ambulanceId) {
        String key = RESERVATION_KEY_PREFIX + hospitalId + ":" + ambulanceId;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "reserved",
                java.time.Duration.ofMinutes(RESERVATION_TTL_MINUTES));

        if (Boolean.TRUE.equals(success)) {
            int updated = hospitalRepository.atomicSoftReserve(hospitalId);
            if (updated > 0) {
                HospitalEntity hospital = hospitalRepository.findById(hospitalId).orElse(null);
                if (hospital != null) {
                    eventPublisher.publishEvent(new BedReservedEvent(
                            hospitalId, ambulanceId, hospital.getName(),
                            hospital.getIcuBeds(), hospital.getSoftReserve(), Instant.now()));
                }
                return true;
            } else {
                redisTemplate.delete(key);
            }
        }
        return false;
    }

    @Override
    @Transactional
    public boolean release(Long hospitalId, Long ambulanceId) {
        if (ambulanceId == null) {
            String pattern = RESERVATION_KEY_PREFIX + hospitalId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } else {
            String key = RESERVATION_KEY_PREFIX + hospitalId + ":" + ambulanceId;
            redisTemplate.delete(key);
        }

        int updated = hospitalRepository.atomicRelease(hospitalId);
        if (updated > 0) {
            HospitalEntity hospital = hospitalRepository.findById(hospitalId).orElse(null);
            if (hospital != null) {
                eventPublisher.publishEvent(new BedReleasedEvent(
                        hospitalId, hospital.getName(),
                        hospital.getIcuBeds(), hospital.getSoftReserve(), Instant.now()));
            }
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    @Scheduled(fixedRate = 60000)
    public void cleanupStaleReservations() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<AmbulanceEntity> enRouteAmbulances = ambulanceRepository.findAll().stream()
                .filter(amb -> AmbulanceStatus.EN_ROUTE.getValue().equals(amb.getStatus()))
                .toList();

        for (AmbulanceEntity amb : enRouteAmbulances) {
            java.time.LocalDateTime createdAt = amb.getCreatedAt();
            if (createdAt == null) {
                continue;
            }
            double etaMinutes = amb.getEtaMinutes() != null ? amb.getEtaMinutes() : 0.0;
            long elapsedMinutes = java.time.Duration.between(createdAt, java.time.LocalDateTime.now()).toMinutes();

            if (elapsedMinutes > (etaMinutes + 10.0)) {
                // Timeout exceeded! Update ambulance status
                amb.setStatus(AmbulanceStatus.TIMEOUT.getValue());
                ambulanceRepository.save(amb);

                Long hospitalId = amb.getDestinationHospitalId();
                if (hospitalId != null) {
                    boolean isCritical = "critical".equalsIgnoreCase(amb.getPatientSeverity());
                    if (isCritical) {
                        // Release the bed
                        release(hospitalId, amb.getId());
                    }

                    // Log the reservation timeout event
                    LogEntity logEntry = new LogEntity("reservation_timeout");
                    logEntry.setAmbulanceId(amb.getId());
                    logEntry.setHospitalSelectedId(hospitalId);
                    logEntry.setDetails("Reservation timed out after " + elapsedMinutes + " min without arrival");
                    logEntry.setTimestamp(java.time.LocalDateTime.now());
                    logRepository.save(logEntry);

                    // Publish standard AlertEvent so WebSocket broadcasts the alert message
                    HospitalEntity hospital = hospitalRepository.findById(hospitalId).orElse(null);
                    String hospitalName = hospital != null ? hospital.getName() : "Hospital #" + hospitalId;
                    eventPublisher.publishEvent(new AlertEvent(
                            "Timeout: Ambulance " + amb.getId() + " failed to arrive. " +
                            (isCritical ? "Bed released" : "No bed reserved") + " at " + hospitalName + ".",
                            "info", Instant.now()));
                }
            }
        }
    }
}
