package com.aerovhyn.common.dto;

import java.time.Instant;

public record ErrorResponseDto(
        Instant timestamp,
        int status,
        String error,
        String detail,
        String path,
        String requestId
) {}
