package com.yaho.factchecker.domain.retrieval.service.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * < 코퍼스 적재 진입점 >
 * 등록된 모든 CorpusIngester(브리핑·연설문 등)를 실행하고 결과를 모음
 * 수동 트리거로 호출 (스케줄러는 추후 구현 예정)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorpusIngestionService {

    private final List<CorpusIngester> ingesters;

    /**
     * 등록된 모든 적재기를 지정 페이지 범위만큼 실행
     * numOfRows = 페이지당 조회 수
     * maxPages =  적재기별 최대 페이지 수
     * 최종반환 결과 = 적재기별 결과 목록
     */
    public List<IngestionResult> ingestAll(int numOfRows, int maxPages) {
        log.info("========== 코퍼스 적재 시작 (numOfRows={}, maxPages={}, 적재기={}개) ==========",
                numOfRows, maxPages, ingesters.size());

        List<IngestionResult> results = new ArrayList<>();
        for (CorpusIngester ingester : ingesters) {
            try {
                results.add(ingester.ingest(numOfRows, maxPages));
            } catch (Exception e) {
                // 한 적재기 전체 실패가 다른 적재기를 막지 않도록
                log.error("[{}] 적재기 실행 실패", ingester.apiName(), e);
                results.add(new IngestionResult(ingester.apiName(), 0, 0, 0, 0));
            }
        }

        log.info("========== 코퍼스 적재 종료 ==========");
        results.forEach(r -> log.info("  결과: {}", r));
        return results;
    }
}
