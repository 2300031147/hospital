package com.aerovhyn.common.dto;

public record VerificationResultDto(
        boolean valid,
        Long firstInvalidIndex,
        int blocksChecked,
        long chainLength,
        String latestHash,
        String message
) {}
