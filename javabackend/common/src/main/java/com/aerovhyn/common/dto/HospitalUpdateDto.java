package com.aerovhyn.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HospitalUpdateDto(
        Integer icuBeds,
        Integer totalIcuBeds,
        Integer ventilators,
        Integer totalVentilators,
        java.util.List<String> specialists,
        Integer currentLoad,
        Integer maxCapacity,
        Double equipmentScore,
        String status
) {}
