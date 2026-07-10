package com.yaho.factchecker.domain.scoring.dto;

import com.yaho.factchecker.global.type.Verdict;
import java.util.UUID;

/**
 * 전체 점수 계산 과정에서 사용된 주장별 요약 정보
 *
 * @param claimId 주장 ID
 * @param canonicalClaim 정제된 주장
 * @param finalScore 주장별 최종 점수
 * @param verdict 주장별 판정
 * @param claimWeight 전체 점수 계산에 반영된 최종 가중치
 * @param included 전체 점수 계산에 포함되었는지 여부
 * @param excludedReason 제외 사유
 */
public record ClaimScoreSummary(
    UUID claimId,
    String canonicalClaim,
    Integer finalScore,
    Verdict verdict,
    double claimWeight,
    boolean included,
    String excludedReason
) {
}
