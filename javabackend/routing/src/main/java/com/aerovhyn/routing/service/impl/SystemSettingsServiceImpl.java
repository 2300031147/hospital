package com.aerovhyn.routing.service.impl;

import com.aerovhyn.common.dto.SystemSettingsDto;
import com.aerovhyn.routing.service.SystemSettingsService;
import com.aerovhyn.domain.entity.SystemSettingsEntity;
import com.aerovhyn.domain.repository.SystemSettingsRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SystemSettingsServiceImpl implements SystemSettingsService {

    private final SystemSettingsRepository settingsRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SystemSettingsServiceImpl(SystemSettingsRepository settingsRepository, ApplicationEventPublisher eventPublisher) {
        this.settingsRepository = settingsRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public SystemSettingsDto getSettings() {
        SystemSettingsEntity entity = settingsRepository.findById(1L).orElse(new SystemSettingsEntity());
        return new SystemSettingsDto(
                entity.getDistanceWeight(),
                entity.getReadinessWeight(),
                entity.getSeverityMatchWeight(),
                entity.getMaxRoutingDistanceKm()
        );
    }

    @Override
    public SystemSettingsDto updateSettings(SystemSettingsDto settings) {
        SystemSettingsEntity entity = settingsRepository.findById(1L).orElse(new SystemSettingsEntity());
        entity.setDistanceWeight(settings.distanceWeight());
        entity.setReadinessWeight(settings.readinessWeight());
        entity.setSeverityMatchWeight(settings.severityMatchWeight());
        entity.setMaxRoutingDistanceKm(settings.maxRoutingDistanceKm());
        settingsRepository.save(entity);

        eventPublisher.publishEvent(new com.aerovhyn.common.events.AlertEvent(
                "Engine configuration parameters updated.", "info", Instant.now()));

        return settings;
    }
}
