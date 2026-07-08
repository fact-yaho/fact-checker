package com.yaho.factchecker.domain.scoring.dto;

import com.yaho.factchecker.global.type.Verdict;
import java.util.List;

/**
 * 여러 주장에 대한 전체 종합 점수 계산 결과
 *
 * @param finalScore 전체 최종 점수
 * @param verdict 전체 최종 판정
 * @param baseScore 패널티 적용 전 가중 평균 점수
 * @param penaltyScore 적용된 패널티 점수
 * @param totalClaimCount 전체 주장 수
 * @param scorableClaimCount 점수 계산에 포함된 주장 수
 * @param insufficientClaimCount 근거 부족 주장 수
 * @param claimScores 주장별 점수 요약
 * @param scoringVersion 점수 계산 버전
 */
public record OverallScoreCalculationResult(
    Integer finalScore,
    Verdict verdict,
    Double baseScore,
    Double penaltyScore,
    int totalClaimCount,
    int scorableClaimCount,
    int insufficientClaimCount,
    List<ClaimScoreSummary> claimScores,
    String scoringVersion
) {

    public static OverallScoreCalculationResult insufficient(
        int totalClaimCount,
        int insufficientClaimCount,
        List<ClaimScoreSummary> claimScores,
        String scoringVersion
    ) {
        return new OverallScoreCalculationResult(
            null,
            Verdict.INSUFFICIENT,
            null,
            null,
            totalClaimCount,
            0,
            insufficientClaimCount,
            claimScores,
            scoringVersion
        );
    }
}
