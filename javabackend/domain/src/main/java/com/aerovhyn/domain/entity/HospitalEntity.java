package com.aerovhyn.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "hospitals")
public class HospitalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lon;

    @Column(columnDefinition = "integer default 0")
    private Integer icuBeds = 0;

    @Column(columnDefinition = "integer default 10")
    private Integer totalIcuBeds = 10;

    @Column(columnDefinition = "integer default 0")
    private Integer softReserve = 0;

    @Column(columnDefinition = "integer default 0")
    private Integer ventilators = 0;

    @Column(columnDefinition = "integer default 5")
    private Integer totalVentilators = 5;

    @Column(columnDefinition = "text default '[]'")
    private String specialists = "[]";

    @Column(columnDefinition = "integer default 0")
    private Integer currentLoad = 0;

    @Column(columnDefinition = "integer default 100")
    private Integer maxCapacity = 100;

    @Column(columnDefinition = "double precision default 0.8")
    private Double equipmentScore = 0.8;

    @Column(columnDefinition = "varchar(255) default 'active'")
    private String status = "active";

    @Column(columnDefinition = "timestamp default CURRENT_TIMESTAMP")
    private LocalDateTime lastUpdated = LocalDateTime.now();

    public HospitalEntity() {}

    public HospitalEntity(String name, Double lat, Double lon) {
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
    public Integer getIcuBeds() { return icuBeds; }
    public void setIcuBeds(Integer icuBeds) { this.icuBeds = icuBeds; }
    public Integer getTotalIcuBeds() { return totalIcuBeds; }
    public void setTotalIcuBeds(Integer totalIcuBeds) { this.totalIcuBeds = totalIcuBeds; }
    public Integer getSoftReserve() { return softReserve; }
    public void setSoftReserve(Integer softReserve) { this.softReserve = softReserve; }
    public Integer getVentilators() { return ventilators; }
    public void setVentilators(Integer ventilators) { this.ventilators = ventilators; }
    public Integer getTotalVentilators() { return totalVentilators; }
    public void setTotalVentilators(Integer totalVentilators) { this.totalVentilators = totalVentilators; }
    public String getSpecialists() { return specialists; }
    public void setSpecialists(String specialists) { this.specialists = specialists; }
    public Integer getCurrentLoad() { return currentLoad; }
    public void setCurrentLoad(Integer currentLoad) { this.currentLoad = currentLoad; }
    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }
    public Double getEquipmentScore() { return equipmentScore; }
    public void setEquipmentScore(Double equipmentScore) { this.equipmentScore = equipmentScore; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
