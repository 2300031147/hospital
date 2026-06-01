package com.aerovhyn.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ambulances")
public class AmbulanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "varchar(255) default 'AMB-001'")
    private String name = "AMB-001";

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lon;

    @Column(columnDefinition = "varchar(255) default 'unknown'")
    private String patientSeverity = "unknown";

    @Column(name = "destination_hospital_id")
    private Long destinationHospitalId;

    @Column(name = "emergency_type")
    private String emergencyType;

    @Column(columnDefinition = "varchar(255) default 'idle'")
    private String status = "idle";

    @Column(columnDefinition = "text default '{}'")
    private String patientVitals = "{}";

    @Column(columnDefinition = "double precision default 0")
    private Double etaMinutes = 0.0;

    @Column(columnDefinition = "timestamp default CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    public AmbulanceEntity() {}

    public AmbulanceEntity(String name, Double lat, Double lon) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }
    public String getPatientSeverity() { return patientSeverity; }
    public void setPatientSeverity(String patientSeverity) { this.patientSeverity = patientSeverity; }
    public Long getDestinationHospitalId() { return destinationHospitalId; }
    public void setDestinationHospitalId(Long destinationHospitalId) { this.destinationHospitalId = destinationHospitalId; }
    public String getEmergencyType() { return emergencyType; }
    public void setEmergencyType(String emergencyType) { this.emergencyType = emergencyType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPatientVitals() { return patientVitals; }
    public void setPatientVitals(String patientVitals) { this.patientVitals = patientVitals; }
    public Double getEtaMinutes() { return etaMinutes; }
    public void setEtaMinutes(Double etaMinutes) { this.etaMinutes = etaMinutes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
