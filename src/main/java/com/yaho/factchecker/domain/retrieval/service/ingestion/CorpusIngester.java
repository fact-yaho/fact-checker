package com.yaho.factchecker.domain.retrieval.service.ingestion;

/**
 * 검색 미지원 API를 코퍼스로 적재하는 적재기
 * API 페이징 조회 → 정제 → fact 추출 → 임베딩 → 저장을 담당
 */
public interface CorpusIngester {

    // 적재기가 담당하는 API 이름 (로그/식별용, ingestion_failure 매칭용) */
    String apiName();

    /**
     * 앞 페이지부터 적재 (대량 초기 적재용)
     * numOfRows = 페이지당 조회 수
     * maxPages = 조회할 최대 페이지 수 (1페이지부터)
     * 반환 = 적재 결과 요약
     */
    IngestionResult ingest(int numOfRows, int maxPages);

    /**
     * 최신 문서 쪽만 적재 (스케줄러 증분 적재용)
     * 대상 API 가 오래된 순 정렬이므로, 마지막 페이지부터 recentPages 페이지만 조회한다.
     * numOfRows = 페이지당 조회 수
     * recentPages = 마지막 페이지부터 조회할 페이지 수
     * 반환 = 적재 결과 요약
     */
    IngestionResult ingestRecent(int numOfRows, int recentPages);

    /**
     * 실패 기록의 원본 item JSON 하나를 복원해 재처리
     * itemJson = ingestion_failure 에 저장된 원본 item JSON 스냅샷
     * 반환 = 저장된 fact 수 (재처리 성공 시). 실패하면 예외를 던져 호출측이 처리
     */
    int retryOne(String itemJson);
}
