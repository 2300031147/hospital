package com.aerovhyn.routing.service;

import com.aerovhyn.common.dto.HospitalInfoDto;
import com.aerovhyn.common.dto.RankedHospitalDto;
import com.aerovhyn.common.dto.SeverityResultDto;
import com.aerovhyn.common.dto.SystemSettingsDto;
import com.aerovhyn.common.enums.EmergencyType;

import java.util.List;

public interface HospitalRanker {
    List<RankedHospitalDto> rank(List<HospitalInfoDto> hospitals, double ambulanceLat,
                                  double ambulanceLon, SeverityResultDto severity,
                                  EmergencyType emergencyType,
                                  SystemSettingsDto settings);
}
