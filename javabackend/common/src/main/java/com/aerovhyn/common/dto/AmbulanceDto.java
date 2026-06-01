package com.aerovhyn.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AmbulanceDto(
        Long id,
        String name,
        double lat,
        double lon,
        String patientSeverity,
        Long destinationHospitalId,
        String emergencyType,
        String status,
        String patientVitals,
        Double etaMinutes,
        String createdAt
) {}
