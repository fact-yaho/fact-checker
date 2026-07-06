package com.yaho.factchecker.domain.retrieval.service.ingestion;

/**
 * FETCH 실패 재처리용 스냅샷
 * 개별 문서 조회 API 가 없어 실패한 원본 item 은 item JSON 으로 보관하지만,
 * FETCH 실패는 "어느 페이지 조회가 실패했는지"를 알아야 재조회가 가능하다.
 * 이 요청 정보를 ingestion_failure 에 JSON 으로 저장해, 재처리 시 해당 페이지를 다시 조회한다.
 *
 * apiName = 실패한 API 이름 (로그·식별용)
 * pageNo = 실패한 페이지 번호
 * numOfRows = 페이지당 조회 수
 */
public record FetchRequest(String apiName, int pageNo, int numOfRows) {}
