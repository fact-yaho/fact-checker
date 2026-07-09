package com.yaho.factchecker.domain.result.dto;

import com.yaho.factchecker.domain.result.entity.ClaimAnalysisResult;
import com.yaho.factchecker.global.type.Verdict;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ClaimAnalysisResultResponse(
    UUID id,
    UUID claimId,
    String claimText,
    Integer displayOrder,
    Double finalScore,
    Verdict verdict,
    String answerSummary,
    String explanation,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    ScoreBreakdownResponse scoreBreakdown,
    List<AnalysisEvidenceResponse> evidences
) {

    public static ClaimAnalysisResultResponse of(
        ClaimAnalysisResult claimResult,
        ScoreBreakdownResponse scoreBreakdown,
        List<AnalysisEvidenceResponse> evidences
    ) {
        return new ClaimAnalysisResultResponse(
            claimResult.getId(),
            claimResult.getClaimId(),
            claimResult.getClaimText(),
            claimResult.getDisplayOrder(),
            claimResult.getFinalScore(),
            claimResult.getVerdict(),
            claimResult.getAnswerSummary(),
            claimResult.getExplanation(),
            claimResult.getCreatedAt(),
            claimResult.getUpdatedAt(),
            scoreBreakdown,
            evidences
        );
    }
}
