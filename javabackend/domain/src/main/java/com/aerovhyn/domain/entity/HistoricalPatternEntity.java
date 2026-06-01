package com.aerovhyn.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "historical_patterns",
       uniqueConstraints = @UniqueConstraint(columnNames = {"hospital_id", "day_of_week", "hour_of_day"}))
public class HistoricalPatternEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "hour_of_day", nullable = false)
    private Integer hourOfDay;

    @Column(nullable = false)
    private Double avgLoad;

    @Column(name = "avg_turnover_rate", columnDefinition = "double precision default 0.05")
    private Double avgTurnoverRate = 0.05;

    public HistoricalPatternEntity() {}

    public HistoricalPatternEntity(Long hospitalId, Integer dayOfWeek, Integer hourOfDay, Double avgLoad) {
        this.hospitalId = hospitalId;
        this.dayOfWeek = dayOfWeek;
        this.hourOfDay = hourOfDay;
        this.avgLoad = avgLoad;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public Integer getHourOfDay() { return hourOfDay; }
    public void setHourOfDay(Integer hourOfDay) { this.hourOfDay = hourOfDay; }
    public Double getAvgLoad() { return avgLoad; }
    public void setAvgLoad(Double avgLoad) { this.avgLoad = avgLoad; }
    public Double getAvgTurnoverRate() { return avgTurnoverRate; }
    public void setAvgTurnoverRate(Double avgTurnoverRate) { this.avgTurnoverRate = avgTurnoverRate; }
}
