package com.aerovhyn.core.service.impl;

import com.aerovhyn.common.events.AlertEvent;
import com.aerovhyn.common.events.HandoffCompletedEvent;
import com.aerovhyn.common.events.PatientAcceptedEvent;
import com.aerovhyn.common.enums.AmbulanceStatus;
import com.aerovhyn.common.exception.ResourceNotFoundException;
import com.aerovhyn.common.exception.ValidationException;
import com.aerovhyn.core.service.BedReservationService;
import com.aerovhyn.core.service.HandoffService;
import com.aerovhyn.domain.entity.AmbulanceEntity;
import com.aerovhyn.domain.entity.HospitalEntity;
import com.aerovhyn.domain.repository.AmbulanceRepository;
import com.aerovhyn.domain.repository.HospitalRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class HandoffServiceImpl implements HandoffService {

    private final HospitalRepository hospitalRepository;
    private final AmbulanceRepository ambulanceRepository;
    private final BedReservationService bedReservationService;
    private final ApplicationEventPublisher eventPublisher;

    public HandoffServiceImpl(
            HospitalRepository hospitalRepository,
            AmbulanceRepository ambulanceRepository,
            BedReservationService bedReservationService,
            ApplicationEventPublisher eventPublisher) {
        this.hospitalRepository = hospitalRepository;
        this.ambulanceRepository = ambulanceRepository;
        this.bedReservationService = bedReservationService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void acknowledge(Long hospitalId) {
        HospitalEntity hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital", hospitalId));
        eventPublisher.publishEvent(new com.aerovhyn.common.events.HandoffAcknowledgedEvent(
                hospitalId, hospital.getName(), Instant.now()));
    }

    @Override
    @Transactional
    public void accept(Long hospitalId, Long ambulanceId) {
        AmbulanceEntity ambulance = ambulanceRepository.findById(ambulanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Ambulance", ambulanceId));

        if (ambulance.getDestinationHospitalId() == null || !ambulance.getDestinationHospitalId().equals(hospitalId)) {
            throw new ValidationException("Ambulance is not routed to this hospital");
        }
        if (AmbulanceStatus.ACCEPTED.getValue().equals(ambulance.getStatus())) {
            throw new ValidationException("Patient already accepted");
        }
        if (!AmbulanceStatus.EN_ROUTE.getValue().equals(ambulance.getStatus())) {
            throw new ValidationException("Ambulance is not en route");
        }

        ambulance.setStatus(AmbulanceStatus.ACCEPTED.getValue());
        ambulanceRepository.save(ambulance);

        HospitalEntity hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital", hospitalId));
        hospital.setCurrentLoad(hospital.getCurrentLoad() + 1);
        if (hospital.getSoftReserve() > 0) {
            hospital.setSoftReserve(hospital.getSoftReserve() - 1);
        }
        hospitalRepository.save(hospital);

        eventPublisher.publishEvent(new PatientAcceptedEvent(
                hospitalId, hospital.getName(), ambulanceId, Instant.now()));
    }

    @Override
    public void releaseBed(Long hospitalId) {
        bedReservationService.release(hospitalId, null);
    }

    @Override
    @Transactional
    public void discharge(Long hospitalId) {
        HospitalEntity hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital", hospitalId));

        if (hospital.getCurrentLoad() > 0) {
            hospital.setCurrentLoad(hospital.getCurrentLoad() - 1);
            hospital.setIcuBeds(hospital.getIcuBeds() + 1);
            hospitalRepository.save(hospital);
        }
    }

    @Override
    @Transactional
    public void completeDispatch(Long ambulanceId) {
        AmbulanceEntity ambulance = ambulanceRepository.findById(ambulanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Ambulance", ambulanceId));

        Long hospitalId = ambulance.getDestinationHospitalId();
        ambulance.setStatus(AmbulanceStatus.IDLE.getValue());
        ambulance.setDestinationHospitalId(null);
        ambulance.setPatientSeverity(null);
        ambulance.setEmergencyType(null);
        ambulance.setPatientVitals("{}");
        ambulance.setEtaMinutes(0.0);
        ambulanceRepository.save(ambulance);

        if (hospitalId != null) {
            HospitalEntity hospital = hospitalRepository.findById(hospitalId).orElse(null);
            if (hospital != null) {
                eventPublisher.publishEvent(new HandoffCompletedEvent(
                        ambulanceId, hospitalId, hospital.getName(), Instant.now()));
            }
        }
    }
}
