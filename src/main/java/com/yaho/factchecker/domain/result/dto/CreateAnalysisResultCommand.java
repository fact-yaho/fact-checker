package com.yaho.factchecker.domain.result.dto;

import com.yaho.factchecker.global.type.InputType;
import com.yaho.factchecker.global.type.Verdict;
import java.util.List;
import java.util.UUID;

public record CreateAnalysisResultCommand(
    UUID userId,
    UUID claimId,

    InputType inputType,
    String originalInput,
    String claimText,
    String sourceUrl,

    Double finalScore,
    Verdict verdict,
    String questionSummary,
    String answerSummary,
    String explanation,

    String modelVersion,
    String scoringVersion,

    List<CreateAnalysisEvidenceCommand> evidences,
    CreateScoreBreakdownCommand scoreBreakdown
) {
}
