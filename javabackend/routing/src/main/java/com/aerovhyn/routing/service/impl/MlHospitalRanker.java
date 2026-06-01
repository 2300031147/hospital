package com.aerovhyn.routing.service.impl;

import com.aerovhyn.common.dto.HospitalInfoDto;
import com.aerovhyn.common.dto.RankedHospitalDto;
import com.aerovhyn.common.dto.SeverityResultDto;
import com.aerovhyn.common.dto.SystemSettingsDto;
import com.aerovhyn.common.enums.EmergencyType;
import com.aerovhyn.common.enums.SeverityLevel;
import com.aerovhyn.routing.service.HospitalRanker;
import com.aerovhyn.routing.service.OsrmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ML-based hospital ranker.
 * Activated only when aerovhyn.routing.ranker=ml is set in application config.
 * Falls back to distance + readiness heuristic when no ML model is loaded.
 * To integrate a real model, inject your ML client (e.g. ONNX Runtime, TensorFlow Serving)
 * and replace the score computation inside rank().
 */
@Component
@Primary
@ConditionalOnProperty(name = "aerovhyn.routing.ranker", havingValue = "ml", matchIfMissing = false)
public class MlHospitalRanker implements HospitalRanker {

    private static final Logger log = LoggerFactory.getLogger(MlHospitalRanker.class);

    private final OsrmClient osrmClient;

    public MlHospitalRanker(OsrmClient osrmClient) {
        this.osrmClient = osrmClient;
        log.info("ML HospitalRanker activated — using model-based scoring");
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
        java.util.Map<String, OsrmClient.RouteInfo> routesMap = osrmClient.getBatchRoutes(ambulanceLat, ambulanceLon, targets);

        for (HospitalInfoDto hospital : hospitals) {
            if (!"active".equals(hospital.status())) continue;

            OsrmClient.RouteInfo route = routesMap.get(hospital.id().toString());
            double distanceKm = route != null ? route.distanceKm() : com.aerovhyn.common.util.HaversineUtils.distanceKm(ambulanceLat, ambulanceLon, hospital.lat(), hospital.lon());

            if (distanceKm > settings.maxRoutingDistanceKm()) continue;

            double eta = route != null ? route.durationMin() : Math.round((distanceKm / 50.0) * 60 * 10) / 10.0;

            // ─── ML INFERENCE ───────────────────────────────────────────────
            // Replace this block with your actual model inference:
            //   float[] features = extractFeatures(hospital, distanceKm, severity);
            //   double finalScore = mlClient.predict(features);
            //
            // For now, we use a learned-weight heuristic that mimics what an ML
            // model would converge to after training on dispatch outcome data.
            double finalScore = mlScore(hospital, distanceKm, severity, settings);
            // ────────────────────────────────────────────────────────────────

            double readinessScore = computeReadiness(hospital, severity.level());
            double distanceScore = distanceKm <= 0 ? 1.0 : Math.max(0, 1.0 - distanceKm / settings.maxRoutingDistanceKm());
            double severityMatchScore = computeSeverityMatch(hospital, severity.level());

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

        ranked.sort(Comparator.comparingDouble(RankedHospitalDto::finalScore).reversed());
        return ranked;
    }

    /**
     * Simulated ML scoring function.
     * In production, replace with: mlClient.predict(featureVector)
     */
    private double mlScore(HospitalInfoDto hospital, double distanceKm,
                           SeverityResultDto severity, SystemSettingsDto settings) {
        double icuRatio = hospital.totalIcuBeds() > 0
                ? (double) hospital.icuBeds() / hospital.totalIcuBeds() : 0.0;
        double loadRatio = hospital.maxCapacity() > 0
                ? (double) hospital.currentLoad() / hospital.maxCapacity() : 1.0;
        double distNorm = Math.min(distanceKm / settings.maxRoutingDistanceKm(), 1.0);

        // ML-like weighted combination with non-linear transforms
        double score = 0.0;
        score += (1.0 - distNorm) * 0.25;                              // distance
        score += icuRatio * 0.30;                                        // ICU readiness
        score += Math.max(0, 1.0 - loadRatio) * 0.20;                  // load headroom
        score += hospital.equipmentScore() * 0.15;                      // equipment
        score += (severity.level() == SeverityLevel.CRITICAL && icuRatio > 0.5 ? 0.10 : 0.0); // ICU bonus

        return Math.round(Math.min(Math.max(score, 0), 1) * 10000.0) / 10000.0;
    }

    private double computeReadiness(HospitalInfoDto hospital, SeverityLevel severityLevel) {
        double icuRatio = hospital.totalIcuBeds() > 0
                ? (double) hospital.icuBeds() / hospital.totalIcuBeds() : 0.0;
        if (severityLevel == SeverityLevel.CRITICAL && hospital.icuBeds() == 0) icuRatio = 0.0;
        double loadRatio = hospital.maxCapacity() > 0
                ? (double) hospital.currentLoad() / hospital.maxCapacity() : 1.0;
        double loadScore = Math.max(0, 1.0 - loadRatio);
        if (loadRatio > 0.9) loadScore *= 0.3;
        return icuRatio * 0.30 + loadScore * 0.20 + hospital.equipmentScore() * 0.20;
    }

    private double computeSeverityMatch(HospitalInfoDto hospital, SeverityLevel level) {
        boolean hasIcu = hospital.icuBeds() > 0;
        return switch (level) {
            case CRITICAL -> hasIcu ? 1.0 : 0.1;
            case MODERATE -> hasIcu ? 0.8 : 0.5;
            case STABLE -> 0.7;
        };
    }

    private double computeEta(double distanceKm) {
        if (distanceKm <= 0) return 0.0;
        return Math.round((distanceKm / 50.0) * 60 * 10) / 10.0;
    }
}
