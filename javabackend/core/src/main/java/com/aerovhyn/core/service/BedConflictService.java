package com.aerovhyn.core.service;

import com.aerovhyn.common.dto.SeverityResultDto;

public interface BedConflictService {
    boolean resolveConflict(Long hospitalId, Long newAmbulanceId, SeverityResultDto newSeverity, double newDistanceKm);
}
