package com.yaho.factchecker.domain.result.dto;

import com.yaho.factchecker.domain.result.code.EvidenceSourceType;
import com.yaho.factchecker.global.type.Stance;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateAnalysisEvidenceCommand(
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
}
