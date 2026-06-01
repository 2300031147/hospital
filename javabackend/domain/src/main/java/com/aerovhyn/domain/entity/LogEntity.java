package com.aerovhyn.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "logs")
public class LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "timestamp default CURRENT_TIMESTAMP")
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(nullable = false)
    private String eventType;

    @Column(name = "ambulance_id")
    private Long ambulanceId;

    @Column(name = "hospital_selected_id")
    private Long hospitalSelectedId;

    private Double score;

    @Column(columnDefinition = "text default ''")
    private String details = "";

    public LogEntity() {}

    public LogEntity(String eventType) {
        this.eventType = eventType;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Long getAmbulanceId() { return ambulanceId; }
    public void setAmbulanceId(Long ambulanceId) { this.ambulanceId = ambulanceId; }
    public Long getHospitalSelectedId() { return hospitalSelectedId; }
    public void setHospitalSelectedId(Long hospitalSelectedId) { this.hospitalSelectedId = hospitalSelectedId; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
