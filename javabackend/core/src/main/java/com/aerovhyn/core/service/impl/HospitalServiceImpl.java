package com.aerovhyn.core.service.impl;

import com.aerovhyn.common.dto.HospitalCreateDto;
import com.aerovhyn.common.dto.HospitalInfoDto;
import com.aerovhyn.common.dto.HospitalUpdateDto;
import com.aerovhyn.common.events.HospitalsUpdatedEvent;
import com.aerovhyn.common.exception.ResourceNotFoundException;
import com.aerovhyn.common.exception.ValidationException;
import com.aerovhyn.core.service.HospitalService;
import com.aerovhyn.domain.entity.AmbulanceEntity;
import com.aerovhyn.domain.entity.UserEntity;
import com.aerovhyn.domain.repository.AmbulanceRepository;
import com.aerovhyn.domain.repository.UserRepository;
import com.aerovhyn.domain.entity.HospitalEntity;
import com.aerovhyn.domain.entity.HistoricalPatternEntity;
import com.aerovhyn.domain.repository.HospitalRepository;
import com.aerovhyn.domain.repository.HistoricalPatternRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class HospitalServiceImpl implements HospitalService {

    private final HospitalRepository hospitalRepository;
    private final HistoricalPatternRepository historicalPatternRepository;
    private final AmbulanceRepository ambulanceRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public HospitalServiceImpl(
            HospitalRepository hospitalRepository,
            HistoricalPatternRepository historicalPatternRepository,
            AmbulanceRepository ambulanceRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.hospitalRepository = hospitalRepository;
        this.historicalPatternRepository = historicalPatternRepository;
        this.ambulanceRepository = ambulanceRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<HospitalInfoDto> getAll(String status) {
        List<HospitalEntity> hospitals;
        if (status != null && !status.isEmpty()) {
            hospitals = hospitalRepository.findAllByStatus(status);
        } else {
            hospitals = hospitalRepository.findAll();
        }
        return hospitals.stream().map(this::toDto).toList();
    }

    @Override
    public HospitalInfoDto getById(Long id) {
        HospitalEntity entity = hospitalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital", id));
        return toDto(entity);
    }

    @Override
    @Transactional
    public HospitalInfoDto create(HospitalCreateDto dto) {
        HospitalEntity entity = new HospitalEntity();
        entity.setName(dto.name());
        entity.setLat(dto.lat());
        entity.setLon(dto.lon());
        entity.setIcuBeds(dto.icuBeds());
        entity.setTotalIcuBeds(dto.totalIcuBeds());
        entity.setVentilators(dto.ventilators());
        entity.setTotalVentilators(dto.totalVentilators());
        String specialistsStr = "[]";
        if (dto.specialists() != null) {
            try {
                specialistsStr = objectMapper.writeValueAsString(dto.specialists());
            } catch (Exception e) {
                specialistsStr = dto.specialists().toString();
            }
        }
        entity.setSpecialists(specialistsStr);
        entity.setCurrentLoad(dto.currentLoad());
        entity.setMaxCapacity(dto.maxCapacity());
        entity.setEquipmentScore(dto.equipmentScore());
        entity.setStatus(dto.status() != null ? dto.status() : "active");
        entity.setLastUpdated(LocalDateTime.now());

        HospitalEntity saved = hospitalRepository.save(entity);

        // Seed historical patterns
        for (int day = 0; day < 7; day++) {
            for (int hour = 0; hour < 24; hour++) {
                double baseLoad = 0.6;
                if (hour >= 18 && hour <= 23) baseLoad += 0.2;
                if (day >= 5) baseLoad += 0.1;
                historicalPatternRepository.save(
                        new HistoricalPatternEntity(saved.getId(), day, hour, Math.min(baseLoad, 1.0))
                );
            }
        }

        invalidateCache();
        eventPublisher.publishEvent(new HospitalsUpdatedEvent(
                saved.getId(), saved.getName(), saved.getIcuBeds(),
                saved.getVentilators(), saved.getCurrentLoad(), saved.getSoftReserve(),
                saved.getStatus(), Instant.now()));
        return toDto(saved);
    }

    @Override
    @Transactional
    public HospitalInfoDto update(Long id, HospitalUpdateDto dto) {
        HospitalEntity entity = hospitalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital", id));

        if (dto.icuBeds() != null) entity.setIcuBeds(dto.icuBeds());
        if (dto.totalIcuBeds() != null) entity.setTotalIcuBeds(dto.totalIcuBeds());
        if (dto.ventilators() != null) entity.setVentilators(dto.ventilators());
        if (dto.totalVentilators() != null) entity.setTotalVentilators(dto.totalVentilators());
        if (dto.specialists() != null) {
            try {
                entity.setSpecialists(objectMapper.writeValueAsString(dto.specialists()));
            } catch (Exception e) {
                entity.setSpecialists(dto.specialists().toString());
            }
        }
        if (dto.currentLoad() != null) entity.setCurrentLoad(dto.currentLoad());
        if (dto.maxCapacity() != null) entity.setMaxCapacity(dto.maxCapacity());
        if (dto.equipmentScore() != null) entity.setEquipmentScore(dto.equipmentScore());
        if (dto.status() != null) entity.setStatus(dto.status());
        entity.setLastUpdated(LocalDateTime.now());

        HospitalEntity saved = hospitalRepository.save(entity);
        invalidateCache();
        eventPublisher.publishEvent(new HospitalsUpdatedEvent(
                saved.getId(), saved.getName(), saved.getIcuBeds(),
                saved.getVentilators(), saved.getCurrentLoad(), saved.getSoftReserve(),
                saved.getStatus(), Instant.now()));
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        HospitalEntity entity = hospitalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital", id));

        // Check for active incoming ambulances — prevent deletion if any
        long activeAmbulances = ambulanceRepository.findByDestinationHospitalId(id).stream()
                .filter(a -> !"completed".equals(a.getStatus()) && !"idle".equals(a.getStatus()))
                .count();
        if (activeAmbulances > 0) {
            throw new ValidationException("Cannot delete hospital with active incoming ambulances");
        }

        // Delete associated historical patterns
        historicalPatternRepository.deleteByHospitalId(id);

        // Nullify user attachments
        List<UserEntity> attachedUsers = userRepository.findByHospitalId(id);
        for (UserEntity user : attachedUsers) {
            user.setHospitalId(null);
            userRepository.save(user);
        }

        hospitalRepository.delete(entity);
        invalidateCache();
        eventPublisher.publishEvent(new HospitalsUpdatedEvent(
                entity.getId(), entity.getName(), entity.getIcuBeds(),
                entity.getVentilators(), entity.getCurrentLoad(), entity.getSoftReserve(),
                entity.getStatus(), Instant.now()));
    }

    private void invalidateCache() {
        try {
            java.util.Set<String> keys = redisTemplate.keys("hospitals:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {}
    }

    private HospitalInfoDto toDto(HospitalEntity entity) {
        List<String> specialists = parseSpecialists(entity.getSpecialists());
        return new HospitalInfoDto(
                entity.getId(),
                entity.getName(),
                entity.getLat(),
                entity.getLon(),
                entity.getIcuBeds(),
                entity.getTotalIcuBeds(),
                entity.getSoftReserve(),
                entity.getVentilators(),
                entity.getTotalVentilators(),
                specialists,
                entity.getCurrentLoad(),
                entity.getMaxCapacity(),
                entity.getEquipmentScore(),
                entity.getStatus(),
                entity.getLastUpdated() != null ? entity.getLastUpdated().toString() : null
        );
    }

    private List<String> parseSpecialists(String specialistsJson) {
        if (specialistsJson == null || specialistsJson.isBlank() || "[]".equals(specialistsJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(specialistsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
