package com.yaho.factchecker.domain.retrieval.dto;

import com.yaho.factchecker.global.type.ClaimCategory;
import java.util.List;
import java.util.UUID;

// 근거검색(retrieval) 요청 단위 = 소주장 1개.
// LLM 분석 결과(claims[] 원소)에 대응하며, claimId는 claim-analysis를 담당하는 사람이 전달
public record ClaimRetrievalRequest(
        UUID claimId,                  // 소주장 식별자 (claim 테이블의 PK)
        String canonicalClaim,         // 정제된 표준 주장 (=소주장 하나)
        boolean isVerifiable,          // 검증 가능 여부
        String unverifiableReason,     // 검증 불가 사유
        ClaimCategory category,        // 카테고리
        List<CountryInfo> countries,   // 대상 국가
        Integer fromYear,              // 검색 연도 하한 (inclusive, null = 하한 없음)
        Integer toYear                 // 검색 연도 상한 (inclusive, null = 상한 없음)
) {
    public record CountryInfo(String name, String code) {}
}
