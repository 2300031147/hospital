package com.aerovhyn.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SystemSettingsDto(
        @Min(0) @Max(1) double distanceWeight,
        @Min(0) @Max(1) double readinessWeight,
        @Min(0) @Max(1) double severityMatchWeight,
        @Min(5) @Max(200) double maxRoutingDistanceKm
) {
    public SystemSettingsDto {
        if (Math.abs(distanceWeight + readinessWeight + severityMatchWeight - 1.0) > 0.01) {
            throw new com.aerovhyn.common.exception.ValidationException("Weights must sum to 1.0");
        }
    }

    public static SystemSettingsDto defaults() {
        return new SystemSettingsDto(0.2, 0.5, 0.3, 30.0);
    }
}
