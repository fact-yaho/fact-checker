package com.yaho.factchecker.global.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * < 근거문서의 출처 유형 >
 * - PER_CLAIM = 소주장별로 조건 검색(country 등)해서 수집한 문서, claim_id 로 범위 한정하여 검색
 * - CORPUS = 소주장과 무관하게 사전 적재한 코퍼스 문서(보도자료·브리핑 등), 벡터로 전체에서 검색
 */
@Getter
@RequiredArgsConstructor
public enum SourceType {

    PER_CLAIM("소주장별 수집"),
    CORPUS("코퍼스 적재");

    private final String label;
}
