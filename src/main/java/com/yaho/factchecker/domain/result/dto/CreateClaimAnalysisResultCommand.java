package com.yaho.factchecker.domain.result.dto;

import com.yaho.factchecker.global.type.Verdict;
import java.util.List;
import java.util.UUID;

public record CreateClaimAnalysisResultCommand(
    UUID claimId,
    String claimText,
    Integer displayOrder,

    /**
     * 소주장별 최종 점수
     */
    Double finalScore,

    /**
     * 소주장별 판정
     */
    Verdict verdict,

    String answerSummary,
    String explanation,

    CreateScoreBreakdownCommand scoreBreakdown,
    List<CreateAnalysisEvidenceCommand> evidences
) {
}
