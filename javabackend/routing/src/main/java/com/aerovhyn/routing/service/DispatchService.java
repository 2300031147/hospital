package com.aerovhyn.routing.service;

import com.aerovhyn.common.dto.PatientVitalsDto;
import com.aerovhyn.common.dto.RouteRequestDto;
import com.aerovhyn.common.dto.RouteResponseDto;
import com.aerovhyn.common.dto.SeverityResultDto;

public interface DispatchService {
    RouteResponseDto routeAmbulance(RouteRequestDto request, Long ambulanceId);
    SeverityResultDto classify(PatientVitalsDto vitals);
}
