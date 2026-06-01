package com.aerovhyn.common.dto;

import com.aerovhyn.common.enums.SeverityLevel;
import java.util.List;

public record SeverityResultDto(
        SeverityLevel level,
        double score,
        List<String> reasons
) {}
