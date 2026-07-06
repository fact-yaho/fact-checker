package com.yaho.factchecker.domain.retrieval.service.ingestion;

/**
 * 검색 미지원 API를 코퍼스로 적재하는 적재기
 * API 페이징 조회 → 정제 → fact 추출 → 임베딩 → 저장을 담당
 */
public interface CorpusIngester {

    // 적재기가 담당하는 API 이름 (로그/식별용, ingestion_failure 매칭용)
    String apiName();

    /**
     * 지정한 페이지 범위만큼 적재
     * numOfRows = 페이지당 조회 수
     * maxPages = 조회할 최대 페이지 수 (1페이지부터)
     * 반환 = 적재 결과 요약 (성공/실패 건수)
     */
    IngestionResult ingest(int numOfRows, int maxPages);

    /**
     * 실패 기록의 원본 item JSON 하나를 복원해 재처리
     * (재처리 서비스가 apiName으로 담당 적재기를 찾아 호출)
     * itemJson = ingestion_failure에 저장된 원본 item JSON 스냅샷
     * 반환 = 저장된 fact 수 (재처리 성공 시), 실패하면 예외를 던져 호출측이 처리
     */
    int retryOne(String itemJson);
}
