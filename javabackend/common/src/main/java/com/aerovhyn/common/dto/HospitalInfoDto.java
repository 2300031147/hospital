package com.aerovhyn.common.dto;

import java.util.List;

public record HospitalInfoDto(
        Long id,
        String name,
        double lat,
        double lon,
        int icuBeds,
        int totalIcuBeds,
        int softReserve,
        int ventilators,
        int totalVentilators,
        List<String> specialists,
        int currentLoad,
        int maxCapacity,
        double equipmentScore,
        String status,
        String lastUpdated
) implements java.io.Serializable {
    public HospitalInfoDto sanitize() {
        return new HospitalInfoDto(
            this.id(),
            this.name(),
            this.lat(),
            this.lon(),
            0, // icuBeds
            0, // totalIcuBeds
            0, // softReserve
            0, // ventilators
            0, // totalVentilators
            java.util.List.of(), // specialists
            0, // currentLoad
            0, // maxCapacity
            0.0, // equipmentScore
            this.status(),
            null // lastUpdated
        );
    }
}
