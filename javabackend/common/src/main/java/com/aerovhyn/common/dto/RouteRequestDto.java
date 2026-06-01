package com.aerovhyn.common.dto;

import java.util.List;

public record RouteRequestDto(
        double ambulanceLat,
        double ambulanceLon,
        PatientVitalsDto vitals
) {}
