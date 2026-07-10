package com.yaho.factchecker.domain.scoring.policy;

import com.yaho.factchecker.domain.scoring.dto.ScoreCalculationResult;
import com.yaho.factchecker.global.type.Verdict;
import org.springframework.stereotype.Component;

/**
 * 전체 종합 점수 계산 정책
 */
@Component
public class OverallScoringPolicy {

    private static final double MIN_CLAIM_IMPORTANCE_WEIGHT = 0.5;
    private static final double MAX_CLAIM_IMPORTANCE_WEIGHT = 2.0;

    private static final int STRONG_INCONSISTENCY_THRESHOLD = 15;
    private static final int WEAK_INCONSISTENCY_THRESHOLD = 40;

    private static final double STRONG_INCONSISTENCY_PENALTY = 8.0;
    private static final double WEAK_INCONSISTENCY_PENALTY = 4.0;

    private static final double MAX_INSUFFICIENT_PENALTY = 10.0;
    private static final double MAX_TOTAL_PENALTY = 25.0;

    private static final double INSUFFICIENT_RESULT_RATIO_THRESHOLD = 0.5;

    /**
     * 전체 점수 계산에 포함 가능한 주장인지 확인합니다.
     */
    public boolean isScorableClaim(ScoreCalculationResult scoreResult) {
        if (scoreResult == null) {
            return false;
        }

        if (scoreResult.finalScore() == null) {
            return false;
        }

        return scoreResult.verdict() != Verdict.INSUFFICIENT
            && scoreResult.verdict() != Verdict.OUT_OF_SCOPE;
    }

    /**
     * 근거 부족으로 볼 주장인지 확인합니다.
     */
    public boolean isInsufficientClaim(ScoreCalculationResult scoreResult) {
        if (scoreResult == null) {
            return true;
        }

        return scoreResult.finalScore() == null
            || scoreResult.verdict() == Verdict.INSUFFICIENT;
    }

    /**
     * 근거 부족 비율이 너무 높으면 전체 결과도 근거 부족으로 처리합니다.
     */
    public boolean shouldReturnInsufficient(
        int totalClaimCount,
        int scorableClaimCount,
        int insufficientClaimCount
    ) {
        if (totalClaimCount == 0) {
            return true;
        }

        if (scorableClaimCount == 0) {
            return true;
        }

        double insufficientRatio = (double) insufficientClaimCount / totalClaimCount;

        return insufficientRatio >= INSUFFICIENT_RESULT_RATIO_THRESHOLD;
    }

    /**
     * 주장 중요도와 근거 충분성을 반영한 최종 claimWeight를 계산합니다.
     */
    public double claimWeight(
        double importanceWeight,
        ScoreCalculationResult scoreResult
    ) {
        double safeImportanceWeight = clamp(
            importanceWeight,
            MIN_CLAIM_IMPORTANCE_WEIGHT,
            MAX_CLAIM_IMPORTANCE_WEIGHT
        );

        double evidenceSufficiencyWeight = evidenceSufficiencyWeight(scoreResult);

        return safeImportanceWeight * evidenceSufficiencyWeight;
    }

    /**
     * 유효 근거 개수가 많을수록 약간 더 높은 가중치를 부여합니다.
     * 단, 근거 개수가 많다는 이유만으로 전체 점수를 과도하게 지배하지 않도록 상한을 둡니다.
     */
    private double evidenceSufficiencyWeight(ScoreCalculationResult scoreResult) {
        if (scoreResult == null) {
            return 1.0;
        }

        int validEvidenceCount = scoreResult.validEvidenceCount();

        return 1.0 + Math.min(validEvidenceCount, 3) * 0.1;
    }

    /**
     * 낮은 점수 주장과 근거 부족 주장 비율을 바탕으로 전체 점수 패널티를 계산합니다.
     */
    public double penalty(
        int strongInconsistencyCount,
        int weakInconsistencyCount,
        int insufficientClaimCount,
        int totalClaimCount
    ) {
        double inconsistencyPenalty =
            strongInconsistencyCount * STRONG_INCONSISTENCY_PENALTY
                + weakInconsistencyCount * WEAK_INCONSISTENCY_PENALTY;

        double insufficientRatio = totalClaimCount == 0
            ? 0.0
            : (double) insufficientClaimCount / totalClaimCount;

        double insufficientPenalty = insufficientRatio * MAX_INSUFFICIENT_PENALTY;

        return Math.min(
            inconsistencyPenalty + insufficientPenalty,
            MAX_TOTAL_PENALTY
        );
    }

    public boolean isStrongInconsistency(int score) {
        return score < STRONG_INCONSISTENCY_THRESHOLD;
    }

    public boolean isWeakInconsistency(int score) {
        return score < WEAK_INCONSISTENCY_THRESHOLD;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
