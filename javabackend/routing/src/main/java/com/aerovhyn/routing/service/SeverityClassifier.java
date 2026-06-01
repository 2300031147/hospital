package com.aerovhyn.routing.service;

import com.aerovhyn.common.dto.PatientVitalsDto;
import com.aerovhyn.common.dto.SeverityResultDto;

public interface SeverityClassifier {
    SeverityResultDto classify(PatientVitalsDto vitals);
}
