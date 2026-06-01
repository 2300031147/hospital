package com.aerovhyn.core.service;

import com.aerovhyn.common.dto.AmbulanceDto;

import java.util.List;

public interface AmbulanceService {
    List<AmbulanceDto> getAll();
    AmbulanceDto getById(Long id);
    AmbulanceDto create(String name, double lat, double lon);
    AmbulanceDto updatePosition(Long id, double lat, double lon);
    void updateStatus(Long id, String status, Long destinationHospitalId, String severity,
                      String emergencyType, String patientVitals, double etaMinutes);
}
