package com.aerovhyn.domain.repository;

import com.aerovhyn.domain.entity.HospitalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HospitalRepository extends JpaRepository<HospitalEntity, Long> {

    List<HospitalEntity> findAllByStatus(String status);

    Optional<HospitalEntity> findByIdAndStatus(Long id, String status);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE HospitalEntity h SET h.icuBeds = h.icuBeds - 1, h.softReserve = h.softReserve + 1 WHERE h.id = :id AND h.icuBeds > 0")
    int atomicSoftReserve(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE HospitalEntity h SET h.icuBeds = h.icuBeds + 1, h.softReserve = h.softReserve - 1 WHERE h.id = :id AND h.softReserve > 0")
    int atomicRelease(@org.springframework.data.repository.query.Param("id") Long id);
}
