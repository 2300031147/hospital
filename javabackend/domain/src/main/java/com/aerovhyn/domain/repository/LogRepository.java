package com.aerovhyn.domain.repository;

import com.aerovhyn.domain.entity.LogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogRepository extends JpaRepository<LogEntity, Long> {

    List<LogEntity> findByEventType(String eventType);

    @Query("SELECT COUNT(l) FROM LogEntity l WHERE l.eventType = ?1")
    long countByEventType(String eventType);

    @Query("SELECT COUNT(l) FROM LogEntity l WHERE l.eventType IN ?1")
    long countByEventTypes(List<String> eventTypes);

    @Query("SELECT AVG(l.score) FROM LogEntity l WHERE l.score IS NOT NULL")
    Double findAverageScore();

    Page<LogEntity> findAllByOrderByTimestampDesc(Pageable pageable);
}
