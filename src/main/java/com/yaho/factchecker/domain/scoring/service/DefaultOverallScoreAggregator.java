package com.yaho.factchecker.domain.scoring.service;

import com.yaho.factchecker.domain.scoring.OverallScoreAggregator;
import com.yaho.factchecker.domain.scoring.dto.ClaimScoreInput;
import com.yaho.factchecker.domain.scoring.dto.ClaimScoreSummary;
import com.yaho.factchecker.domain.scoring.dto.OverallScoreCalculationResult;
import com.yaho.factchecker.domain.scoring.dto.ScoreCalculationResult;
import com.yaho.factchecker.domain.scoring.policy.OverallScoringPolicy;
import com.yaho.factchecker.domain.scoring.policy.ScoringPolicy;
import com.yaho.factchecker.global.type.Verdict;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultOverallScoreAggregator implements OverallScoreAggregator {

    private final ScoringPolicy scoringPolicy;
    private final OverallScoringPolicy overallScoringPolicy;

    @Override
    public OverallScoreCalculationResult aggregate(List<ClaimScoreInput> claimScores) {
        List<ClaimScoreInput> safeClaimScores = claimScores == null
            ? List.of()
            : claimScores;

        if (safeClaimScores.isEmpty()) {
            return OverallScoreCalculationResult.insufficient(
                0,
                0,
                List.of(),
                scoringPolicy.scoringVersion()
            );
        }

        validateClaimScores(safeClaimScores);

        List<ClaimScoreSummary> claimScoreSummaries = safeClaimScores.stream()
            .map(this::toClaimScoreSummary)
            .toList();

        List<ClaimScoreSummary> scorableClaimScores = claimScoreSummaries.stream()
            .filter(ClaimScoreSummary::included)
            .toList();

        int totalClaimCount = claimScoreSummaries.size();
        int scorableClaimCount = scorableClaimScores.size();
        int insufficientClaimCount = (int) safeClaimScores.stream()
            .filter(claimScore -> overallScoringPolicy.isInsufficientClaim(claimScore.scoreResult()))
            .count();

        if (overallScoringPolicy.shouldReturnInsufficient(
            totalClaimCount,
            scorableClaimCount,
            insufficientClaimCount
        )) {
            return OverallScoreCalculationResult.insufficient(
                totalClaimCount,
                insufficientClaimCount,
                claimScoreSummaries,
                scoringPolicy.scoringVersion()
            );
        }

        double weightSum = scorableClaimScores.stream()
            .mapToDouble(ClaimScoreSummary::claimWeight)
            .sum();

        if (weightSum <= 0.0) {
            return OverallScoreCalculationResult.insufficient(
                totalClaimCount,
                insufficientClaimCount,
                claimScoreSummaries,
                scoringPolicy.scoringVersion()
            );
        }

        double weightedScoreSum = scorableClaimScores.stream()
            .mapToDouble(claimScore -> claimScore.finalScore() * claimScore.claimWeight())
            .sum();

        double baseScore = weightedScoreSum / weightSum;

        int strongInconsistencyCount = (int) scorableClaimScores.stream()
            .filter(claimScore -> overallScoringPolicy.isStrongInconsistency(claimScore.finalScore()))
            .count();

        int weakInconsistencyCount = (int) scorableClaimScores.stream()
            .filter(claimScore -> overallScoringPolicy.isWeakInconsistency(claimScore.finalScore()))
            .count();

        double penaltyScore = overallScoringPolicy.penalty(
            strongInconsistencyCount,
            weakInconsistencyCount,
            insufficientClaimCount,
            totalClaimCount
        );

        int finalScore = (int) Math.round(clamp(baseScore - penaltyScore, 0.0, 100.0));
        Verdict verdict = scoringPolicy.determineVerdict(finalScore);

        return new OverallScoreCalculationResult(
            finalScore,
            verdict,
            round(baseScore),
            round(penaltyScore),
            totalClaimCount,
            scorableClaimCount,
            insufficientClaimCount,
            claimScoreSummaries,
            scoringPolicy.scoringVersion()
        );
    }

    private ClaimScoreSummary toClaimScoreSummary(ClaimScoreInput claimScore) {
        ScoreCalculationResult scoreResult = claimScore.scoreResult();

        if (!overallScoringPolicy.isScorableClaim(scoreResult)) {
            return new ClaimScoreSummary(
                claimScore.claimId(),
                claimScore.canonicalClaim(),
                scoreResult == null ? null : scoreResult.finalScore(),
                scoreResult == null ? Verdict.INSUFFICIENT : scoreResult.verdict(),
                0.0,
                false,
                "전체 점수 계산에 포함할 수 없는 주장입니다."
            );
        }

        double claimWeight = overallScoringPolicy.claimWeight(
            claimScore.importanceWeight(),
            scoreResult
        );

        return new ClaimScoreSummary(
            claimScore.claimId(),
            claimScore.canonicalClaim(),
            scoreResult.finalScore(),
            scoreResult.verdict(),
            round(claimWeight),
            true,
            null
        );
    }

    private void validateClaimScores(List<ClaimScoreInput> claimScores) {
        for (ClaimScoreInput claimScore : claimScores) {
            if (claimScore == null) {
                throw new IllegalArgumentException("ClaimScoreInput must not be null.");
            }

            if (Double.isNaN(claimScore.importanceWeight())
                || Double.isInfinite(claimScore.importanceWeight())
                || claimScore.importanceWeight() <= 0.0) {
                throw new IllegalArgumentException("importanceWeight must be greater than 0.");
            }
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
