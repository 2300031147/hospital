package com.aerovhyn.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserCreateDto(
        String username,
        String password,
        String fullName,
        String role,
        Long ambulanceId,
        Long hospitalId
) {}
