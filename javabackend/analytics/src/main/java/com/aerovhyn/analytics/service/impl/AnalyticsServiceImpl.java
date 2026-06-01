package com.aerovhyn.analytics.service.impl;

import com.aerovhyn.analytics.service.AnalyticsService;
import com.aerovhyn.common.dto.AnalyticsResponseDto;
import com.aerovhyn.domain.entity.HospitalEntity;
import com.aerovhyn.domain.repository.HospitalRepository;
import com.aerovhyn.domain.repository.LogRepository;
import com.aerovhyn.domain.repository.AmbulanceRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final LogRepository logRepository;
    private final HospitalRepository hospitalRepository;
    private final AmbulanceRepository ambulanceRepository;

    public AnalyticsServiceImpl(LogRepository logRepository, HospitalRepository hospitalRepository, AmbulanceRepository ambulanceRepository) {
        this.logRepository = logRepository;
        this.hospitalRepository = hospitalRepository;
        this.ambulanceRepository = ambulanceRepository;
    }

    @Override
    public AnalyticsResponseDto buildAnalytics() {
        long totalDispatches = logRepository.countByEventType("ambulance_routed");
        long totalReroutes = logRepository.countByEventTypes(List.of("ambulance_rerouted", "conflict_resolved"));

        // Severity distribution from active ambulances
        Map<String, Integer> severityDist = new HashMap<>();
        List<Object[]> dist = ambulanceRepository.getSeverityDistribution();
        if (dist != null) {
            for (Object[] row : dist) {
                String severity = (String) row[0];
                if (severity == null || severity.isBlank()) severity = "unknown";
                long count = (Long) row[1];
                severityDist.put(severity, (int) count);
            }
        }

        // Hospital utilization
        List<HospitalEntity> hospitals = hospitalRepository.findAll();
        List<Map<String, Object>> utilization = hospitals.stream().map(h -> {
            int maxCap = h.getMaxCapacity() > 0 ? h.getMaxCapacity() : 1;
            return Map.<String, Object>of(
                    "id", h.getId(),
                    "name", h.getName(),
                    "load_pct", Math.round((double) h.getCurrentLoad() / maxCap * 1000) / 10.0,
                    "icu_available", h.getIcuBeds(),
                    "icu_total", h.getTotalIcuBeds(),
                    "reserved", h.getSoftReserve()
            );
        }).toList();

        Double avgScore = logRepository.findAverageScore();
        double avgScoreVal = avgScore != null ? Math.round(avgScore * 10000.0) / 10000.0 : 0.0;

        int recentEvents = (int) logRepository.count();

        double rerouteRate = Math.round((double) totalReroutes / Math.max(totalDispatches, 1) * 1000) / 10.0;

        return new AnalyticsResponseDto(
                (int) totalDispatches,
                (int) totalReroutes,
                severityDist,
                utilization,
                avgScoreVal,
                rerouteRate,
                recentEvents
        );
    }
}
