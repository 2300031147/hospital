package com.aerovhyn.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RouteResponseDto(
        Long ambulanceId,
        SeverityResultDto severity,
        List<RankedHospitalDto> rankedHospitals,
        @JsonProperty("selected_hospital") HospitalInfoDto recommended
) {}
