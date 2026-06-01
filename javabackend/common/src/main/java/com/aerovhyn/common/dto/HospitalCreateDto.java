package com.aerovhyn.common.dto;

import java.util.Map;

public record HospitalCreateDto(
        String name,
        double lat,
        double lon,
        int icuBeds,
        int totalIcuBeds,
        int ventilators,
        int totalVentilators,
        java.util.List<String> specialists,
        int currentLoad,
        int maxCapacity,
        double equipmentScore,
        String status
) {}
