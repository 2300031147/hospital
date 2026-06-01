package com.aerovhyn.common.dto;

import java.util.List;

public record HandoffAlertDto(
        Long ambulanceId,
        Long hospitalId,
        String hospitalName,
        SeverityResultDto severity,
        PatientVitalsDto vitals,
        double etaMinutes,
        List<String> prepInstructions,
        boolean bedReserved
) {}
