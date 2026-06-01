package com.aerovhyn.analytics.service;

import com.aerovhyn.common.dto.BlockchainBlockDto;
import com.aerovhyn.common.dto.VerificationResultDto;

import java.util.List;
import java.util.Map;

public interface BlockchainAuditService {
    BlockchainBlockDto addBlock(Map<String, Object> data);
    VerificationResultDto verifyChain();
    List<BlockchainBlockDto> getChain(int limit);
}
