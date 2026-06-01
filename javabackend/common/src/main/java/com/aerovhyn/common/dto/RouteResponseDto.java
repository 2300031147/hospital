package com.aerovhyn.common.dto;

import java.util.List;

public record RouteResponseDto(
        Long ambulanceId,
        SeverityResultDto severity,
        List<RankedHospitalDto> rankedHospitals,
        RankedHospitalDto recommended
) {}
