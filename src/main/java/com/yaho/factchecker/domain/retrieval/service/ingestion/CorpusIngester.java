package com.yaho.factchecker.domain.retrieval.service.ingestion;

/**
 * 검색 기능이 지원되지 않는 API를 코퍼스로 적재하는 적재기
 * API 페이징 조회 → 정제 → fact 추출 → 임베딩 → 저장을 담당
 */
public interface CorpusIngester {

    /** 적재기가 담당하는 API 이름 (로그/식별용) */
    String apiName();

    /**
     * 지정한 페이지 범위만큼 적재
     * numOfRows = 페이지당 조회 수
     * maxPages = 조회할 최대 페이지 수 (1페이지부터)
     * 최종 반환 내용 = 적재 결과 요약 (성공/실패 건수)
     */
    IngestionResult ingest(int numOfRows, int maxPages);
}
