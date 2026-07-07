package com.yaho.factchecker.domain.retrieval.service.collector;

import com.yaho.factchecker.domain.retrieval.dto.ClaimRetrievalRequest;
import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.global.type.ClaimCategory;

import java.util.List;
import java.util.Set;

/**
 * 공공데이터 API 하나에 대응하는 근거문서 수집기
 * 각 구현체는 "자신이 담당하는 카테고리 목록"과 "실제 수집 로직"을 정의
 * DocumentCollector 가 소주장 카테고리에 맞는 수집기만 골라 collect()를 호출
 */
public interface ApiDocumentCollector {

    // 수집기가 담당하는 카테고리들, 소주장 category가 여기 포함되면 호출
    Set<ClaimCategory> supportedCategories();

    /**
     * 소주장 정보로 API를 호출해 근거문서를 수집
     * 저장은 하지 않고 EvidenceDocument 목록만 반환(저장은 DocumentCollector가 일괄 처리)
     * 실패 시 예외를 던지지 않고 빈 리스트 반환(한 API 실패가 전체를 막지 않도록)
     */
    List<EvidenceDocument> collect(ClaimRetrievalRequest request);
}
