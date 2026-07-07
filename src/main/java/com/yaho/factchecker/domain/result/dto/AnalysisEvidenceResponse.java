package com.yaho.factchecker.domain.result.dto;

import com.yaho.factchecker.domain.result.code.EvidenceSourceType;
import com.yaho.factchecker.domain.result.entity.AnalysisEvidence;
import com.yaho.factchecker.global.type.Stance;
import java.time.LocalDateTime;
import java.util.UUID;

public record AnalysisEvidenceResponse(
    UUID id,
    UUID aiLogId,

    EvidenceSourceType sourceType,
    String sourceTitle,
    String sourceUrl,
    String sourceId,

    String snippet,
    Stance stance,
    Double relevanceScore,
    Double similarityScore,
    String judgmentReason,

    LocalDateTime publishedAt,
    Integer displayOrder
) {

    public static AnalysisEvidenceResponse from(AnalysisEvidence evidence) {
        return new AnalysisEvidenceResponse(
            evidence.getId(),
            evidence.getAiLogId(),
            evidence.getSourceType(),
            evidence.getSourceTitle(),
            evidence.getSourceUrl(),
            evidence.getSourceId(),
            evidence.getSnippet(),
            evidence.getStance(),
            evidence.getRelevanceScore(),
            evidence.getSimilarityScore(),
            evidence.getJudgmentReason(),
            evidence.getPublishedAt(),
            evidence.getDisplayOrder()
        );
    }
}
