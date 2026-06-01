package com.aerovhyn.routing.service.impl;

import com.aerovhyn.common.dto.HospitalInfoDto;
import com.aerovhyn.common.dto.RankedHospitalDto;
import com.aerovhyn.common.dto.SeverityResultDto;
import com.aerovhyn.common.dto.SystemSettingsDto;
import com.aerovhyn.common.enums.EmergencyType;
import com.aerovhyn.common.enums.SeverityLevel;
import com.aerovhyn.routing.service.HospitalRanker;
import com.aerovhyn.routing.service.OsrmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Rule-based hospital ranker — the default implementation.
 * Marked @Primary so it is injected unless a higher-priority bean exists.
 * When aerovhyn.routing.ranker=ml, MlHospitalRanker takes over via its own @Primary.
 */
@Component
@Primary
@ConditionalOnProperty(name = "aerovhyn.routing.ranker", havingValue = "rule", matchIfMissing = true)
public class HospitalRankerImpl implements HospitalRanker {

    private static final double DEFAULT_AMBULANCE_SPEED_KMH = 50.0;
    private static final double HIGH_LOAD_THRESHOLD = 0.9;
    private static final double HIGH_LOAD_PENALTY = 0.3;

    private static final double READINESS_ICU_WEIGHT = 0.30;
    private static final double READINESS_LOAD_WEIGHT = 0.20;
    private static final double READINESS_EQUIPMENT_WEIGHT = 0.20;

    private static final double CRITICAL_SEVERITY_WEIGHT_BOOST = 0.4;
    private static final double CRITICAL_READINESS_REDUCTION = 0.1;
    private static final double CRITICAL_MIN_READINESS_WEIGHT = 0.2;

    private static final double FALLBACK_SEVERITY_WEIGHT = 0.34;
    private static final double FALLBACK_READINESS_WEIGHT = 0.33;
    private static final double FALLBACK_DISTANCE_WEIGHT = 0.33; 

    private static final Map<EmergencyType, List<String>> EMERGENCY_SPECIALTY_MAP = Map.of(
            EmergencyType.CARDIAC, List.of("cardiology"),
            EmergencyType.TRAUMA, List.of("trauma", "orthopedics"),
            EmergencyType.RESPIRATORY, List.of("pulmonology"),
            EmergencyType.NEUROLOGICAL, List.of("neurology"),
            EmergencyType.FRACTURE, List.of("orthopedics", "trauma"),
            EmergencyType.BURN, List.of("trauma", "general"),
            EmergencyType.GENERAL, List.of("general")
    );

    private final OsrmClient osrmClient;

    public HospitalRankerImpl(OsrmClient osrmClient) {
        this.osrmClient = osrmClient;
    }

    @Override
    public List<RankedHospitalDto> rank(List<HospitalInfoDto> hospitals, double ambulanceLat,
                                         double ambulanceLon, SeverityResultDto severity,
                                         EmergencyType emergencyType,
                                         SystemSettingsDto settings) {
        if (settings == null) {
            settings = SystemSettingsDto.defaults();
        }

        List<RankedHospitalDto> ranked = new ArrayList<>();

        // Gather active target locations
        List<OsrmClient.HospitalLocation> targets = hospitals.stream()
                .filter(h -> "active".equals(h.status()))
                .map(h -> new OsrmClient.HospitalLocation(h.id().toString(), h.lat(), h.lon()))
                .toList();

        // Query matrix in one batch request
        Map<String, OsrmClient.RouteInfo> routesMap = osrmClient.getBatchRoutes(ambulanceLat, ambulanceLon, targets);

        for (HospitalInfoDto hospital : hospitals) {
            if (!"active".equals(hospital.status())) continue;

            OsrmClient.RouteInfo route = routesMap.get(hospital.id().toString());
            double distanceKm = route != null ? route.distanceKm() : com.aerovhyn.common.util.HaversineUtils.distanceKm(ambulanceLat, ambulanceLon, hospital.lat(), hospital.lon());
            double eta = route != null ? route.durationMin() : Math.round((distanceKm / DEFAULT_AMBULANCE_SPEED_KMH) * 60 * 10) / 10.0;

            // Note: Do NOT filter by distance here — Python includes all active hospitals
            // in ranked results. Far hospitals get distance_score=0, which naturally pushes them down.

            double readinessScore = computeReadiness(hospital, severity.level(), emergencyType, eta);
            double distanceScore = computeDistanceScore(distanceKm, settings.maxRoutingDistanceKm());
            double severityMatchScore = computeSeverityMatch(hospital, severity.level(),
                    EMERGENCY_SPECIALTY_MAP.entrySet().stream()
                            .filter(e -> e.getKey() == emergencyType)
                            .findFirst().map(Map.Entry::getValue)
                            .orElse(List.of("general")));

            double finalScore;
            if (severity.level() == SeverityLevel.CRITICAL) {
                double sWeight = Math.max(settings.severityMatchWeight(), CRITICAL_SEVERITY_WEIGHT_BOOST);
                double rWeight = Math.max(settings.readinessWeight() - CRITICAL_READINESS_REDUCTION, CRITICAL_MIN_READINESS_WEIGHT);
                double dWeight = settings.distanceWeight();
                double total = sWeight + rWeight + dWeight;
                if (total > 0) {
                    sWeight /= total;
                    rWeight /= total;
                    dWeight /= total;
                } else {
                    sWeight = FALLBACK_SEVERITY_WEIGHT;
                    rWeight = FALLBACK_READINESS_WEIGHT;
                    dWeight = FALLBACK_DISTANCE_WEIGHT;
                }
                finalScore = readinessScore * rWeight + distanceScore * dWeight + severityMatchScore * sWeight;
            } else {
                finalScore = readinessScore * settings.readinessWeight()
                        + distanceScore * settings.distanceWeight()
                        + severityMatchScore * settings.severityMatchWeight();
            }

            ranked.add(new RankedHospitalDto(
                    hospital,
                    Math.round(finalScore * 10000.0) / 10000.0,
                    readinessScore,
                    distanceScore,
                    severityMatchScore,
                    Math.round(distanceKm * 100.0) / 100.0,
                    eta
            ));
        }

        ranked.sort((a, b) -> Double.compare(b.finalScore(), a.finalScore()));
        return ranked;
    }

    private double computeReadiness(HospitalInfoDto hospital, SeverityLevel severityLevel, EmergencyType emergencyType, double etaMinutes) {
        double icuScore = hospital.totalIcuBeds() > 0
                ? (double) hospital.icuBeds() / hospital.totalIcuBeds() : 0.0;

        if (severityLevel == SeverityLevel.CRITICAL && hospital.icuBeds() == 0) {
            icuScore = 0.0;
        }

        // Specialist match (0-1): do they have the right specialists?
        List<String> requiredSpecialists = EMERGENCY_SPECIALTY_MAP.getOrDefault(emergencyType, List.of("general"));
        double specialistScore = 0.2; // Fallback
        if (hospital.specialists() != null && !hospital.specialists().isEmpty()) {
            long matches = requiredSpecialists.stream().filter(s -> hospital.specialists().contains(s)).count();
            specialistScore = (double) matches / requiredSpecialists.size();
        }

        // Load prediction (0-1): Standard live load + predictive forecasting
        double predictedLoadRatio = hospital.maxCapacity() > 0
                ? (double) hospital.currentLoad() / hospital.maxCapacity() : 1.0;

        // Introduce Forecasting if wait > 15m. (e.g. night time + weekend = higher load prediction)
        double etaHours = etaMinutes / 60.0;
        double predictedTurnover = 0.05 * etaHours;
        predictedLoadRatio -= predictedTurnover;

        // Penalty for peak hours (18 to 23 IST or weekend)
        java.time.ZonedDateTime nowIst = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        int currentHour = nowIst.getHour();
        boolean isWeekend = nowIst.getDayOfWeek().getValue() >= 6;
        if ((currentHour >= 18 && currentHour <= 23) || isWeekend) {
            predictedLoadRatio += 0.1;
        }

        predictedLoadRatio = Math.max(0.0, Math.min(predictedLoadRatio, 1.0));

        double loadScore = hospital.maxCapacity() > 0 ? Math.max(0.0, 1.0 - predictedLoadRatio) : 0.0;

        if (predictedLoadRatio > HIGH_LOAD_THRESHOLD) {
            loadScore *= HIGH_LOAD_PENALTY;
        }

        double readiness = icuScore * READINESS_ICU_WEIGHT
                + specialistScore * 0.30
                + loadScore * READINESS_LOAD_WEIGHT
                + hospital.equipmentScore() * READINESS_EQUIPMENT_WEIGHT;

        // Uncertainty Penalty for stale data
        if (hospital.lastUpdated() != null && !hospital.lastUpdated().isEmpty()) {
            try {
                java.time.Instant lastUpdatedInstant;
                String clean = hospital.lastUpdated();
                if (clean.contains("T") || clean.contains("-")) {
                    if (!clean.endsWith("Z") && !clean.contains("+")) {
                        java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(clean);
                        lastUpdatedInstant = ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();
                    } else {
                        lastUpdatedInstant = java.time.Instant.parse(clean);
                    }
                } else {
                    lastUpdatedInstant = java.time.Instant.ofEpochMilli(Long.parseLong(clean));
                }
                long diffMinutes = java.time.Duration.between(lastUpdatedInstant, java.time.Instant.now()).toMinutes();
                if (diffMinutes > 30) {
                    readiness *= 0.8; // 20% penalty for stale data
                }
            } catch (Exception e) {
                // ignore
            }
        }

        return Math.round(Math.min(Math.max(readiness, 0.0), 1.0) * 10000.0) / 10000.0;
    }

    private double computeDistanceScore(double distanceKm, double maxDistanceKm) {
        if (distanceKm <= 0) return 1.0;
        if (distanceKm >= maxDistanceKm) return 0.0;
        return Math.round((1.0 - (distanceKm / maxDistanceKm)) * 10000.0) / 10000.0;
    }

    private double computeSeverityMatch(HospitalInfoDto hospital, SeverityLevel level, List<String> requiredSpecialists) {
        boolean hasSpecialists = false;
        if (hospital.specialists() != null) {
            hasSpecialists = requiredSpecialists.stream().anyMatch(s -> hospital.specialists().contains(s));
        }

        return switch (level) {
            case CRITICAL -> {
                if (hospital.icuBeds() >= 2 && hasSpecialists) yield 1.0;
                else if (hospital.icuBeds() >= 1) yield 0.6;
                else if (hasSpecialists) yield 0.4;
                else yield 0.1;
            }
            case MODERATE -> {
                if (hasSpecialists) yield 0.9;
                else if (hospital.icuBeds() >= 1) yield 0.7;
                else yield 0.5;
            }
            case STABLE -> hasSpecialists ? 0.8 : 0.6;
        };
    }

    private double computeEta(double distanceKm) {
        if (distanceKm <= 0) return 0.0;
        return Math.round((distanceKm / DEFAULT_AMBULANCE_SPEED_KMH) * 60 * 10) / 10.0;
    }
}
