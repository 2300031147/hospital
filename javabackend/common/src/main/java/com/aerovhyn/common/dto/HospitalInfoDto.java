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
) implements java.io.Serializable {}
