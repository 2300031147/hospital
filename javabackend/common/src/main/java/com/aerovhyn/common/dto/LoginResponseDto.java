package com.aerovhyn.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponseDto(
        String accessToken,
        String tokenType,
        String role,
        String fullName,
        String username,
        Long userId,
        Long ambulanceId,
        Long hospitalId
) {}
