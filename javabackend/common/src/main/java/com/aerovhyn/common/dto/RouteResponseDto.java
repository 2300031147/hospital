package com.aerovhyn.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RouteResponseDto(
        Long ambulanceId,
        SeverityResultDto severity,
        List<RankedHospitalDto> rankedHospitals,
        @JsonProperty("selected_hospital") HospitalInfoDto recommended
) {
    public RouteResponseDto sanitize() {
        java.util.List<RankedHospitalDto> sanitizedRanked = this.rankedHospitals() != null 
            ? this.rankedHospitals().stream().map(RankedHospitalDto::sanitize).toList()
            : null;
        HospitalInfoDto sanitizedRecommended = this.recommended() != null
            ? this.recommended().sanitize()
            : null;
        return new RouteResponseDto(
            this.ambulanceId(),
            this.severity(),
            sanitizedRanked,
            sanitizedRecommended
        );
    }
}
