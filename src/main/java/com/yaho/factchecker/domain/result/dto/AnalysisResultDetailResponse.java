package com.yaho.factchecker.domain.result.dto;

import com.yaho.factchecker.domain.result.code.AnalysisStatus;
import com.yaho.factchecker.domain.result.entity.AnalysisResult;
import com.yaho.factchecker.global.type.InputType;
import com.yaho.factchecker.global.type.Verdict;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AnalysisResultDetailResponse(
    UUID id,
    UUID userId,

    InputType inputType,
    String originalInput,
    String sourceUrl,

    Double finalScore,
    Verdict verdict,
    String questionSummary,
    String answerSummary,
    String explanation,

    AnalysisStatus analysisStatus,
    String modelVersion,
    String scoringVersion,

    LocalDateTime createdAt,
    LocalDateTime updatedAt,

    List<ClaimAnalysisResultResponse> claimResults
) {

    public static AnalysisResultDetailResponse of(
        AnalysisResult result,
        List<ClaimAnalysisResultResponse> claimResults
    ) {
        return new AnalysisResultDetailResponse(
            result.getId(),
            result.getUserId(),
            result.getInputType(),
            result.getOriginalInput(),
            result.getSourceUrl(),
            result.getFinalScore(),
            result.getVerdict(),
            result.getQuestionSummary(),
            result.getAnswerSummary(),
            result.getExplanation(),
            result.getAnalysisStatus(),
            result.getModelVersion(),
            result.getScoringVersion(),
            result.getCreatedAt(),
            result.getUpdatedAt(),
            claimResults
        );
    }
}
