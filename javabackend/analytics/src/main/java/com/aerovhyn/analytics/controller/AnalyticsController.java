package com.aerovhyn.analytics.controller;

import com.aerovhyn.analytics.service.AnalyticsService;
import com.aerovhyn.analytics.service.BlockchainAuditService;
import com.aerovhyn.common.dto.AnalyticsResponseDto;
import com.aerovhyn.common.dto.BlockchainBlockDto;
import com.aerovhyn.common.dto.VerificationResultDto;
import com.aerovhyn.domain.entity.LogEntity;
import com.aerovhyn.domain.repository.LogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('COMMAND_CENTER') or hasRole('DISPATCHER')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final BlockchainAuditService blockchainAuditService;
    private final LogRepository logRepository;

    public AnalyticsController(
            AnalyticsService analyticsService,
            BlockchainAuditService blockchainAuditService,
            LogRepository logRepository) {
        this.analyticsService = analyticsService;
        this.blockchainAuditService = blockchainAuditService;
        this.logRepository = logRepository;
    }

    @GetMapping("/analytics")
    public AnalyticsResponseDto getAnalytics() {
        return analyticsService.buildAnalytics();
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> getLogs(@RequestParam(defaultValue = "50") int limit) {
        return logRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, limit)).getContent().stream()
                .map(this::logToMap)
                .toList();
    }

    @GetMapping("/audit-log")
    public List<BlockchainBlockDto> getAuditLog(@RequestParam(defaultValue = "50") int limit) {
        return blockchainAuditService.getChain(limit);
    }

    @GetMapping("/audit-log/verify")
    public VerificationResultDto verifyAuditLog() {
        return blockchainAuditService.verifyChain();
    }

    // Frontend expects /api/blockchain and /api/blockchain/verify
    @GetMapping("/blockchain")
    public List<BlockchainBlockDto> getBlockchain(@RequestParam(defaultValue = "50") int limit) {
        return blockchainAuditService.getChain(limit);
    }

    @GetMapping("/blockchain/verify")
    public VerificationResultDto verifyBlockchain() {
        return blockchainAuditService.verifyChain();
    }

    private Map<String, Object> logToMap(LogEntity log) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", log.getId());
        map.put("timestamp", log.getTimestamp() != null ? log.getTimestamp().toString() : null);
        map.put("event_type", log.getEventType());
        map.put("event", log.getEventType());
        map.put("message", log.getEventType());
        map.put("ambulance_id", log.getAmbulanceId());
        map.put("hospital_selected_id", log.getHospitalSelectedId());
        map.put("score", log.getScore());
        map.put("details", log.getDetails());
        return map;
    }
}
