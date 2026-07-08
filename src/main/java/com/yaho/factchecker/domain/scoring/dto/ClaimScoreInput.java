package com.yaho.factchecker.domain.scoring.dto;

import java.util.UUID;

/**
 * 전체 점수 계산에 사용할 주장별 점수 입력 DTO
 *
 * @param claimId 주장 ID
 * @param canonicalClaim 정제된 주장
 * @param scoreResult 해당 주장에 대한 점수 계산 결과
 * @param importanceWeight 주장 중요도 가중치
 */
public record ClaimScoreInput(
    UUID claimId,
    String canonicalClaim,
    ScoreCalculationResult scoreResult,
    double importanceWeight
) {

    private static final double DEFAULT_IMPORTANCE_WEIGHT = 1.0;

    public ClaimScoreInput {
        if (importanceWeight <= 0.0 || Double.isNaN(importanceWeight) || Double.isInfinite(importanceWeight)) {
            throw new IllegalArgumentException("importanceWeight must be greater than 0.");
        }
    }

    public static ClaimScoreInput of(
        UUID claimId,
        String canonicalClaim,
        ScoreCalculationResult scoreResult
    ) {
        return new ClaimScoreInput(
            claimId,
            canonicalClaim,
            scoreResult,
            DEFAULT_IMPORTANCE_WEIGHT
        );
    }

    public static ClaimScoreInput of(
        UUID claimId,
        String canonicalClaim,
        ScoreCalculationResult scoreResult,
        double importanceWeight
    ) {
        return new ClaimScoreInput(
            claimId,
            canonicalClaim,
            scoreResult,
            importanceWeight
        );
    }
}
