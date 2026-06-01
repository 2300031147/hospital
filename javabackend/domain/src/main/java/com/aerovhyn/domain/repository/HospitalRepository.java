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
}
