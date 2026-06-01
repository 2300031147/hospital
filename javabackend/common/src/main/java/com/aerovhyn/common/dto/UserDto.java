package com.aerovhyn.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserDto(
        Long id,
        String username,
        String fullName,
        String role,
        Long ambulanceId,
        Long hospitalId,
        String createdAt
) {}
