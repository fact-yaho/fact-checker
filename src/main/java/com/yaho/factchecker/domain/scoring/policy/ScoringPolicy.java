package com.yaho.factchecker.domain.scoring.policy;

import com.yaho.factchecker.global.type.Stance;
import com.yaho.factchecker.global.type.Verdict;
import org.springframework.stereotype.Component;

/**
 * 점수 계산 정책을 모아둔 클래스
 */
@Component
public class ScoringPolicy {

    private static final String SCORING_VERSION = "v1.1";

    /**
     * 근거 채택 하한선.
     *
     * <p>retrieval의 relevanceScore는 RRF 기반 랭킹 점수로, "의미적 관련도"가 아니라
     * "검색 결과 내 상대 순위"를 반영한다. 따라서 실제로 관련이 깊은 문서라도
     * 랭킹이 중위권이면 0.4 안팎의 값을 갖는다.
     *
     * <p>관련성 판단은 stance(LLM)가 담당하므로(IRRELEVANT/INSUFFICIENT는 방향성 필터에서 배제),
     * 여기서는 명백한 노이즈만 걸러내는 하한선으로만 사용한다.
     * 관련도의 강약은 evidenceWeight(relevance × similarity)에 이미 반영된다.
     */
    private static final double MIN_RELEVANCE_SCORE = 0.3;
    private static final double MIN_SIMILARITY_SCORE = 0.1;

    private static final int MIN_VALID_EVIDENCE_COUNT = 1;

    public String scoringVersion() {
        return SCORING_VERSION;
    }

    public int minValidEvidenceCount() {
        return MIN_VALID_EVIDENCE_COUNT;
    }

    /**
     * 점수 계산에 포함할 근거인지 판단한다.
     *
     * <p>관련성 여부는 stance(LLM 판단)를 1차 기준으로 삼고,
     * relevance/similarity는 명백한 노이즈를 배제하는 하한선으로만 적용한다.
     */
    public boolean isScorableEvidence(
            Stance stance,
            double relevanceScore,
            double similarityScore
    ) {
        if (stance == null) {
            return false;
        }

        // IRRELEVANT / INSUFFICIENT 는 여기서 배제된다.
        if (!isDirectionalStance(stance)) {
            return false;
        }

        return relevanceScore >= MIN_RELEVANCE_SCORE
                && similarityScore >= MIN_SIMILARITY_SCORE;
    }

    public boolean isDirectionalStance(Stance stance) {
        return stance == Stance.SUPPORTS
                || stance == Stance.PARTIAL
                || stance == Stance.CONTRADICTS;
    }

    public double stanceWeight(Stance stance) {
        return switch (stance) {
            case SUPPORTS -> 1.0;
            case PARTIAL -> 0.45;
            case CONTRADICTS -> -1.0;
            case INSUFFICIENT, IRRELEVANT -> 0.0;
        };
    }

    public Verdict determineVerdict(int finalScore) {
        if (finalScore >= 85) {
            return Verdict.CONSISTENT;
        }

        if (finalScore >= 60) {
            return Verdict.PARTIALLY_CONSISTENT;
        }

        if (finalScore >= 40) {
            return Verdict.UNCERTAIN;
        }

        if (finalScore >= 15) {
            return Verdict.PARTIALLY_INCONSISTENT;
        }

        return Verdict.INCONSISTENT;
    }
}
