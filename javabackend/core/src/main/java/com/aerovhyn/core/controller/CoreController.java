package com.aerovhyn.core.controller;

import com.aerovhyn.common.dto.*;
import com.aerovhyn.core.service.AmbulanceService;
import com.aerovhyn.core.service.BedReservationService;
import com.aerovhyn.core.service.HandoffService;
import com.aerovhyn.core.service.HospitalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CoreController {

    private final HospitalService hospitalService;
    private final AmbulanceService ambulanceService;
    private final BedReservationService bedReservationService;
    private final HandoffService handoffService;

    public CoreController(
            HospitalService hospitalService,
            AmbulanceService ambulanceService,
            BedReservationService bedReservationService,
            HandoffService handoffService) {
        this.hospitalService = hospitalService;
        this.ambulanceService = ambulanceService;
        this.bedReservationService = bedReservationService;
        this.handoffService = handoffService;
    }

    @GetMapping("/hospitals")
    @org.springframework.cache.annotation.Cacheable(value = "hospitals", key = "'all'")
    public List<HospitalInfoDto> getHospitals(@RequestParam(required = false) String status) {
        return hospitalService.getAll(status);
    }

    @GetMapping("/hospitals/{hospitalId}")
    public HospitalInfoDto getHospital(@PathVariable Long hospitalId) {
        return hospitalService.getById(hospitalId);
    }

    @PostMapping("/hospitals")
    @PreAuthorize("hasRole('COMMAND_CENTER')")
    public HospitalInfoDto createHospital(@RequestBody HospitalCreateDto dto) {
        return hospitalService.create(dto);
    }

    @PutMapping("/hospitals/{hospitalId}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('COMMAND_CENTER')")
    public HospitalInfoDto updateHospital(
            @PathVariable Long hospitalId, 
            @RequestBody HospitalUpdateDto dto,
            jakarta.servlet.http.HttpServletRequest request) {
        checkHospitalAccess(hospitalId, request);
        return hospitalService.update(hospitalId, dto);
    }

    @DeleteMapping("/hospitals/{hospitalId}")
    @PreAuthorize("hasRole('COMMAND_CENTER')")
    public void deleteHospital(@PathVariable Long hospitalId) {
        hospitalService.delete(hospitalId);
    }

    @GetMapping("/ambulances")
    public List<AmbulanceDto> getAmbulances() {
        return ambulanceService.getAll();
    }

    @PostMapping("/ambulances")
    @PreAuthorize("hasRole('PARAMEDIC') or hasRole('COMMAND_CENTER')")
    public Map<String, Object> createAmbulance(@RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "AMB-001");
        double lat = ((Number) body.get("lat")).doubleValue();
        double lon = ((Number) body.get("lon")).doubleValue();
        AmbulanceDto amb = ambulanceService.create(name, lat, lon);
        return Map.of("id", amb.id(), "name", amb.name());
    }

    @PutMapping("/ambulances/{ambulanceId}/position")
    @PreAuthorize("hasRole('PARAMEDIC') or hasRole('COMMAND_CENTER')")
    public Map<String, String> updatePosition(
            @PathVariable Long ambulanceId, 
            @RequestBody Map<String, Double> pos,
            jakarta.servlet.http.HttpServletRequest request) {
        
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isCommandCenter = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_COMMAND_CENTER"));
                
        if (!isCommandCenter) {
            Long authenticatedAmbId = (Long) request.getAttribute("ambulanceId");
            if (authenticatedAmbId == null || !authenticatedAmbId.equals(ambulanceId)) {
                throw new com.aerovhyn.common.exception.AerovhynException("You can only update your own ambulance", 403);
            }
        }

        ambulanceService.updatePosition(ambulanceId, pos.get("lat"), pos.get("lon"));
        return Map.of("status", "updated");
    }

    @PostMapping("/hospitals/{hospitalId}/acknowledge")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('COMMAND_CENTER')")
    public Map<String, String> acknowledgeHandoff(
            @PathVariable Long hospitalId,
            jakarta.servlet.http.HttpServletRequest request) {
        checkHospitalAccess(hospitalId, request);
        handoffService.acknowledge(hospitalId);
        HospitalInfoDto hospital = hospitalService.getById(hospitalId);
        return Map.of("status", "acknowledged", "hospital_name", hospital.name());
    }

    @PostMapping("/hospitals/{hospitalId}/accept/{ambulanceId}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('COMMAND_CENTER')")
    public Map<String, Object> acceptPatient(
            @PathVariable Long hospitalId, 
            @PathVariable Long ambulanceId,
            jakarta.servlet.http.HttpServletRequest request) {
        checkHospitalAccess(hospitalId, request);
        handoffService.accept(hospitalId, ambulanceId);
        return Map.of("status", "accepted", "ambulance_id", ambulanceId);
    }

    @PostMapping("/hospitals/{hospitalId}/release-bed")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('COMMAND_CENTER')")
    public Map<String, Object> releaseBed(
            @PathVariable Long hospitalId,
            jakarta.servlet.http.HttpServletRequest request) {
        checkHospitalAccess(hospitalId, request);
        boolean released = bedReservationService.release(hospitalId, null);
        if (!released) {
            throw new com.aerovhyn.common.exception.ValidationException("No reserved beds to release");
        }
        HospitalInfoDto hospital = hospitalService.getById(hospitalId);
        return Map.of("status", "released", "hospital", hospital);
    }

    @PostMapping("/hospitals/{hospitalId}/discharge")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('COMMAND_CENTER')")
    public Map<String, String> discharge(
            @PathVariable Long hospitalId,
            jakarta.servlet.http.HttpServletRequest request) {
        checkHospitalAccess(hospitalId, request);
        HospitalInfoDto hospital = hospitalService.getById(hospitalId);
        if (hospital.currentLoad() <= 0) {
            return Map.of("status", "ignored", "message", "Load is already zero");
        }
        handoffService.discharge(hospitalId);
        return Map.of("status", "success", "message", "Patient discharged");
    }

    @PostMapping("/ambulances/{ambulanceId}/complete")
    @PreAuthorize("hasRole('PARAMEDIC') or hasRole('COMMAND_CENTER') or hasRole('DISPATCHER')")
    public Map<String, String> completeRun(
            @PathVariable Long ambulanceId,
            jakarta.servlet.http.HttpServletRequest request) {
        checkAmbulanceAccess(ambulanceId, request);
        handoffService.completeDispatch(ambulanceId);
        return Map.of("status", "completed");
    }

    private void checkHospitalAccess(Long hospitalId, jakarta.servlet.http.HttpServletRequest request) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isCommandCenter = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_COMMAND_CENTER"));
                
        if (!isCommandCenter) {
            Long authenticatedHospId = (Long) request.getAttribute("hospitalId");
            if (authenticatedHospId == null || !authenticatedHospId.equals(hospitalId)) {
                throw new com.aerovhyn.common.exception.AerovhynException("Not authorized for this hospital", 403);
            }
        }
    }

    private void checkAmbulanceAccess(Long ambulanceId, jakarta.servlet.http.HttpServletRequest request) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isPrivileged = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_COMMAND_CENTER") || a.getAuthority().equals("ROLE_DISPATCHER"));
                
        if (!isPrivileged) {
            Long authenticatedAmbId = (Long) request.getAttribute("ambulanceId");
            if (authenticatedAmbId == null || !authenticatedAmbId.equals(ambulanceId)) {
                throw new com.aerovhyn.common.exception.AerovhynException("You can only access your own ambulance", 403);
            }
        }
    }
}
