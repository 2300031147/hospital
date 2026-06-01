package com.aerovhyn.core.service.impl;

import com.aerovhyn.common.dto.AmbulanceDto;
import com.aerovhyn.common.events.AmbulancePositionUpdatedEvent;
import com.aerovhyn.common.exception.ResourceNotFoundException;
import com.aerovhyn.core.service.AmbulanceService;
import com.aerovhyn.domain.entity.AmbulanceEntity;
import com.aerovhyn.domain.repository.AmbulanceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AmbulanceServiceImpl implements AmbulanceService {

    private final AmbulanceRepository ambulanceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AmbulanceServiceImpl(AmbulanceRepository ambulanceRepository, ApplicationEventPublisher eventPublisher) {
        this.ambulanceRepository = ambulanceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<AmbulanceDto> getAll() {
        return ambulanceRepository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    public AmbulanceDto getById(Long id) {
        AmbulanceEntity entity = ambulanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ambulance", id));
        return toDto(entity);
    }

    @Override
    public AmbulanceDto create(String name, double lat, double lon) {
        AmbulanceEntity entity = new AmbulanceEntity(
                name != null ? name : "AMB-001", lat, lon);
        entity.setCreatedAt(LocalDateTime.now());
        return toDto(ambulanceRepository.save(entity));
    }

    @Override
    @Transactional
    public AmbulanceDto updatePosition(Long id, double lat, double lon) {
        AmbulanceEntity entity = ambulanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ambulance", id));
        entity.setLat(lat);
        entity.setLon(lon);
        AmbulanceEntity saved = ambulanceRepository.save(entity);
        eventPublisher.publishEvent(new AmbulancePositionUpdatedEvent(id, lat, lon, Instant.now()));
        return toDto(saved);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status, Long destinationHospitalId, String severity,
                             String emergencyType, String patientVitals, double etaMinutes) {
        AmbulanceEntity entity = ambulanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ambulance", id));
        entity.setStatus(status);
        entity.setDestinationHospitalId(destinationHospitalId);
        entity.setPatientSeverity(severity);
        entity.setEmergencyType(emergencyType);
        entity.setPatientVitals(patientVitals);
        entity.setEtaMinutes(etaMinutes);
        ambulanceRepository.save(entity);
    }

    private AmbulanceDto toDto(AmbulanceEntity entity) {
        return new AmbulanceDto(
                entity.getId(),
                entity.getName(),
                entity.getLat(),
                entity.getLon(),
                entity.getPatientSeverity(),
                entity.getDestinationHospitalId(),
                entity.getEmergencyType(),
                entity.getStatus(),
                entity.getPatientVitals(),
                entity.getEtaMinutes(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null
        );
    }
}
