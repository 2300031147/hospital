package com.aerovhyn.common.dto;

public record RankedHospitalDto(
        HospitalInfoDto hospital,
        double finalScore,
        double readinessScore,
        double distanceScore,
        double severityMatchScore,
        double distanceKm,
        double etaMinutes
) {}
