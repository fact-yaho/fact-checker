package com.yaho.factchecker.domain.result.dto;

import com.yaho.factchecker.global.type.InputType;
import com.yaho.factchecker.global.type.Verdict;
import java.util.List;
import java.util.UUID;

public record CreateAnalysisResultCommand(
    UUID userId,

    InputType inputType,
    String originalInput,
    String sourceUrl,

    /**
     * 소주장별 점수를 종합한 최종 점수
     */
    Double finalScore,

    /**
     * 소주장별 판정을 종합한 최종 판정
     */
    Verdict verdict,

    String questionSummary,
    String answerSummary,
    String explanation,

    String modelVersion,
    String scoringVersion,

    List<CreateClaimAnalysisResultCommand> claimResults
) {
}
