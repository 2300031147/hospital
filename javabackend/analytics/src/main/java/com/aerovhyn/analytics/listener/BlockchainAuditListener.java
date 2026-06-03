package com.aerovhyn.analytics.listener;

import com.aerovhyn.common.events.BlockchainAuditEvent;
import com.aerovhyn.analytics.service.BlockchainAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class BlockchainAuditListener {
    private static final Logger log = LoggerFactory.getLogger(BlockchainAuditListener.class);
    private final BlockchainAuditService blockchainAuditService;

    public BlockchainAuditListener(BlockchainAuditService blockchainAuditService) {
        this.blockchainAuditService = blockchainAuditService;
    }

    @Async
    @EventListener
    public void handleBlockchainAuditEvent(BlockchainAuditEvent event) {
        log.info("Received BlockchainAuditEvent: {}", event.details());
        blockchainAuditService.addBlock(java.util.Map.of("details", event.details(), "event", "system_audit"));
    }
}
