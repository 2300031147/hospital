package com.aerovhyn.common.dto;

import java.util.List;
import java.util.Map;

public record AnalyticsResponseDto(
        int totalDispatches,
        int totalReroutes,
        Map<String, Integer> severityDistribution,
        List<Map<String, Object>> hospitalUtilization,
        double avgScore,
        double rerouteRate,
        int recentEvents
) {}
