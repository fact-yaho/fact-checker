package com.yaho.factchecker.domain.result.dto;

public record CreateScoreBreakdownCommand(
    Double officialConsistencyScore,
    Double evidenceRelevanceScore,
    Double evidenceSufficiencyScore,
    Double recencyScore,
    Double contradictionPenalty
) {
}
