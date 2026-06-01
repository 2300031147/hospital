package com.aerovhyn.domain.repository;

import com.aerovhyn.domain.entity.HistoricalPatternEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HistoricalPatternRepository extends JpaRepository<HistoricalPatternEntity, Long> {

    Optional<HistoricalPatternEntity> findByHospitalIdAndDayOfWeekAndHourOfDay(
            Long hospitalId, Integer dayOfWeek, Integer hourOfDay);

    void deleteByHospitalId(Long hospitalId);
}
