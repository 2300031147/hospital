package com.aerovhyn.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "settings")
public class SystemSettingsEntity {

    @Id
    private Long id = 1L;

    @Column(name = "distance_weight", columnDefinition = "double precision default 0.2")
    private Double distanceWeight = 0.2;

    @Column(name = "readiness_weight", columnDefinition = "double precision default 0.5")
    private Double readinessWeight = 0.5;

    @Column(name = "severity_match_weight", columnDefinition = "double precision default 0.3")
    private Double severityMatchWeight = 0.3;

    @Column(name = "max_routing_distance_km", columnDefinition = "double precision default 30.0")
    private Double maxRoutingDistanceKm = 30.0;

    public SystemSettingsEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Double getDistanceWeight() { return distanceWeight; }
    public void setDistanceWeight(Double distanceWeight) { this.distanceWeight = distanceWeight; }
    public Double getReadinessWeight() { return readinessWeight; }
    public void setReadinessWeight(Double readinessWeight) { this.readinessWeight = readinessWeight; }
    public Double getSeverityMatchWeight() { return severityMatchWeight; }
    public void setSeverityMatchWeight(Double severityMatchWeight) { this.severityMatchWeight = severityMatchWeight; }
    public Double getMaxRoutingDistanceKm() { return maxRoutingDistanceKm; }
    public void setMaxRoutingDistanceKm(Double maxRoutingDistanceKm) { this.maxRoutingDistanceKm = maxRoutingDistanceKm; }
}
