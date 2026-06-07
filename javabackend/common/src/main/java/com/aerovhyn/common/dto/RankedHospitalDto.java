package com.aerovhyn.common.dto;

public record RankedHospitalDto(
        HospitalInfoDto hospital,
        double finalScore,
        double readinessScore,
        double distanceScore,
        double severityMatchScore,
        double distanceKm,
        double etaMinutes
) {
    public RankedHospitalDto sanitize() {
        return new RankedHospitalDto(
            this.hospital() != null ? this.hospital().sanitize() : null,
            0.0, // finalScore
            0.0, // readinessScore
            0.0, // distanceScore
            0.0, // severityMatchScore
            this.distanceKm(),
            this.etaMinutes()
        );
    }
}
