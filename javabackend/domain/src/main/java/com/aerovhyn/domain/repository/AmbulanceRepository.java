package com.aerovhyn.domain.repository;

import com.aerovhyn.domain.entity.AmbulanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AmbulanceRepository extends JpaRepository<AmbulanceEntity, Long> {

    List<AmbulanceEntity> findAllByStatus(String status);

    List<AmbulanceEntity> findByDestinationHospitalId(Long hospitalId);

    @org.springframework.data.jpa.repository.Query("SELECT a.patientSeverity, COUNT(a) FROM AmbulanceEntity a GROUP BY a.patientSeverity")
    List<Object[]> getSeverityDistribution();
}
