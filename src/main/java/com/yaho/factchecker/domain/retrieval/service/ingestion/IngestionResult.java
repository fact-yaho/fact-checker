package com.yaho.factchecker.domain.retrieval.service.ingestion;

/**
 * < 코퍼스 적재 결과 요약 >
 * apiName = 적재한 API
 * totalProcessed = 처리 시도한 문서 수
 * successCount = 문서 저장 + fact 저장까지 성공한 수
 * failureCount = 실패한 문서 수 (로그에 상세 기록됨)
 * savedFactCount = 저장된 fact 총 개수
 */
public record IngestionResult(
        String apiName,
        int totalProcessed,
        int successCount,
        int failureCount,
        int savedFactCount
) {}
