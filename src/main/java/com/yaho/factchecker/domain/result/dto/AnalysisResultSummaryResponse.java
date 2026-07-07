package com.yaho.factchecker.domain.result.dto;

import com.yaho.factchecker.domain.result.code.AnalysisStatus;
import com.yaho.factchecker.domain.result.entity.AnalysisResult;
import com.yaho.factchecker.global.type.Verdict;
import java.time.LocalDateTime;
import java.util.UUID;

public record AnalysisResultSummaryResponse(
    UUID id,
    UUID claimId,
    String questionSummary,
    Double finalScore,
    Verdict verdict,
    AnalysisStatus analysisStatus,
    LocalDateTime createdAt
) {

    public static AnalysisResultSummaryResponse from(AnalysisResult result) {
        return new AnalysisResultSummaryResponse(
            result.getId(),
            result.getClaimId(),
            result.getQuestionSummary(),
            result.getFinalScore(),
            result.getVerdict(),
            result.getAnalysisStatus(),
            result.getCreatedAt()
        );
    }
}
