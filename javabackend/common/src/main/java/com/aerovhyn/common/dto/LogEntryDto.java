package com.aerovhyn.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogEntryDto(
        Long id,
        String timestamp,
        String eventType,
        Long ambulanceId,
        Long hospitalSelectedId,
        Double score,
        String details
) {}
