package com.aerovhyn.routing.controller;

import com.aerovhyn.common.dto.*;
import com.aerovhyn.routing.service.DispatchService;
import com.aerovhyn.routing.service.SystemSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DispatchController {

    private final DispatchService dispatchService;
    private final SystemSettingsService settingsService;

    public DispatchController(DispatchService dispatchService, SystemSettingsService settingsService) {
        this.dispatchService = dispatchService;
        this.settingsService = settingsService;
    }

    @PostMapping("/classify")
    public SeverityResultDto classify(@RequestBody PatientVitalsDto vitals) {
        return dispatchService.classify(vitals);
    }

    @PostMapping("/route")
    @PreAuthorize("hasRole('PARAMEDIC') or hasRole('COMMAND_CENTER') or hasRole('DISPATCHER')")
    public RouteResponseDto route(@RequestBody RouteRequestDto request, HttpServletRequest httpRequest) {
        Long ambulanceId = httpRequest.getAttribute("ambulanceId") instanceof Number n ? n.longValue() : null;
        if (ambulanceId == null && request.ambulanceId() != null) {
            ambulanceId = request.ambulanceId();
        }
        return dispatchService.routeAmbulance(request, ambulanceId);
    }

    @GetMapping("/settings")
    @PreAuthorize("hasRole('COMMAND_CENTER') or hasRole('DISPATCHER')")
    public SystemSettingsDto getSettings() {
        return settingsService.getSettings();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('COMMAND_CENTER')")
    public SystemSettingsDto updateSettings(@RequestBody SystemSettingsDto settings) {
        return settingsService.updateSettings(settings);
    }
}
