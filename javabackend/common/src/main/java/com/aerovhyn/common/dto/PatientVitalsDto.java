package com.aerovhyn.common.dto;

import com.aerovhyn.common.enums.EmergencyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PatientVitalsDto(
        @Min(20) @Max(250) int heartRate,
        @Min(0) @Max(100) int spo2,
        @Min(40) @Max(300) int systolicBp,
        @NotNull EmergencyType emergencyType,
        @Min(0) @Max(120) int age
) {}
