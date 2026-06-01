package com.aerovhyn.common.dto;

public record BlockchainBlockDto(
        long idx,
        String timestamp,
        Object data,
        String prevHash,
        String hash
) {}
