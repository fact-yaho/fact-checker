package com.yaho.factchecker.domain.result.dto;

import com.yaho.factchecker.domain.result.entity.ScoreBreakdown;
import java.util.UUID;

public record ScoreBreakdownResponse(
    UUID id,
    Double officialConsistencyScore,
    Double evidenceRelevanceScore,
    Double evidenceSufficiencyScore,
    Double recencyScore,
    Double contradictionPenalty,
    Double finalScore
) {

    public static ScoreBreakdownResponse from(ScoreBreakdown scoreBreakdown) {
        if (scoreBreakdown == null) {
            return null;
        }

        return new ScoreBreakdownResponse(
            scoreBreakdown.getId(),
            scoreBreakdown.getOfficialConsistencyScore(),
            scoreBreakdown.getEvidenceRelevanceScore(),
            scoreBreakdown.getEvidenceSufficiencyScore(),
            scoreBreakdown.getRecencyScore(),
            scoreBreakdown.getContradictionPenalty(),
            scoreBreakdown.getFinalScore()
        );
    }
}
