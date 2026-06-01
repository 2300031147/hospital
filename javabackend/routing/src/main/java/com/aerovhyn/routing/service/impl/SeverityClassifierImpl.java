package com.aerovhyn.routing.service.impl;

import com.aerovhyn.common.dto.PatientVitalsDto;
import com.aerovhyn.common.dto.SeverityResultDto;
import com.aerovhyn.common.enums.EmergencyType;
import com.aerovhyn.common.enums.SeverityLevel;
import com.aerovhyn.routing.service.SeverityClassifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SeverityClassifierImpl implements SeverityClassifier {

    private static final int SPO2_CRITICAL = 85;
    private static final int SPO2_LOW = 92;
    private static final double SPO2_CRITICAL_SCORE = 0.35;
    private static final double SPO2_LOW_SCORE = 0.15;

    private static final int BP_HYPOTENSION = 90;
    private static final int BP_HYPERTENSION = 180;
    private static final double BP_HYPOTENSION_SCORE = 0.30;
    private static final double BP_HYPERTENSION_SCORE = 0.20;

    private static final int HR_TACHYCARDIA_SEVERE = 150;
    private static final int HR_BRADYCARDIA_SEVERE = 40;
    private static final int HR_TACHYCARDIA = 120;
    private static final double HR_TACHYCARDIA_SEVERE_SCORE = 0.20;
    private static final double HR_BRADYCARDIA_SCORE = 0.25;
    private static final double HR_TACHYCARDIA_SCORE = 0.10;

    private static final int ELDERLY_AGE = 70;
    private static final int PEDIATRIC_AGE = 5;
    private static final double AGE_MODIFIER = 0.08;

    private static final double HIGH_RISK_EMERGENCY_SCORE = 0.10;
    private static final double TRAUMA_EMERGENCY_SCORE = 0.05;

    private static final double CRITICAL_THRESHOLD = 0.55;
    private static final double MODERATE_THRESHOLD = 0.25;

    @Override
    public SeverityResultDto classify(PatientVitalsDto vitals) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();

        if (vitals.spo2() < SPO2_CRITICAL) {
            score += SPO2_CRITICAL_SCORE;
            reasons.add("Dangerously low SpO2: " + vitals.spo2() + "%");
        } else if (vitals.spo2() < SPO2_LOW) {
            score += SPO2_LOW_SCORE;
            reasons.add("Low SpO2: " + vitals.spo2() + "%");
        }

        if (vitals.systolicBp() < BP_HYPOTENSION) {
            score += BP_HYPOTENSION_SCORE;
            reasons.add("Hypotension: BP " + vitals.systolicBp() + " mmHg");
        } else if (vitals.systolicBp() > BP_HYPERTENSION) {
            score += BP_HYPERTENSION_SCORE;
            reasons.add("Hypertensive crisis: BP " + vitals.systolicBp() + " mmHg");
        }

        if (vitals.heartRate() > HR_TACHYCARDIA_SEVERE) {
            score += HR_TACHYCARDIA_SEVERE_SCORE;
            reasons.add("Severe tachycardia: " + vitals.heartRate() + " BPM");
        } else if (vitals.heartRate() < HR_BRADYCARDIA_SEVERE) {
            score += HR_BRADYCARDIA_SCORE;
            reasons.add("Severe bradycardia: " + vitals.heartRate() + " BPM");
        } else if (vitals.heartRate() > HR_TACHYCARDIA) {
            score += HR_TACHYCARDIA_SCORE;
            reasons.add("Tachycardia: " + vitals.heartRate() + " BPM");
        }

        if (vitals.age() > ELDERLY_AGE) {
            score += AGE_MODIFIER;
            reasons.add("Elderly patient: age " + vitals.age());
        } else if (vitals.age() < PEDIATRIC_AGE) {
            score += AGE_MODIFIER;
            reasons.add("Pediatric patient: age " + vitals.age());
        }

        if (vitals.emergencyType() == EmergencyType.CARDIAC || vitals.emergencyType() == EmergencyType.NEUROLOGICAL) {
            score += HIGH_RISK_EMERGENCY_SCORE;
            reasons.add("High-risk emergency type: " + vitals.emergencyType().name().toLowerCase());
        } else if (vitals.emergencyType() == EmergencyType.TRAUMA || vitals.emergencyType() == EmergencyType.BURN) {
            score += TRAUMA_EMERGENCY_SCORE;
            reasons.add("Trauma/burn emergency: " + vitals.emergencyType().name().toLowerCase());
        }

        score = Math.min(score, 1.0);

        SeverityLevel level;
        if (score >= CRITICAL_THRESHOLD) {
            level = SeverityLevel.CRITICAL;
        } else if (score >= MODERATE_THRESHOLD) {
            level = SeverityLevel.MODERATE;
        } else {
            level = SeverityLevel.STABLE;
        }

        if (reasons.isEmpty()) {
            reasons.add("All vitals within normal range");
        }

        return new SeverityResultDto(level, Math.round(score * 1000.0) / 1000.0, reasons);
    }
}
