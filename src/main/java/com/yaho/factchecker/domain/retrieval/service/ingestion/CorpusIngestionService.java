package com.yaho.factchecker.domain.retrieval.service.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * < 코퍼스 적재 진입점 >
 * 등록된 모든 CorpusIngester(브리핑·연설문 등)를 실행하고 결과를 모음
 * 수동 트리거 + 스케줄러(CorpusIngestionScheduler)에서 호출
 *
 * ingestAll = 앞 페이지부터 (대량 초기 적재)
 * ingestAllRecent = 마지막 페이지부터 (스케줄러 증분 — 대상 API가 오래된 순 정렬)
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

    /**
     * 등록된 모든 적재기를 최신 문서 쪽만 실행 (스케줄러 증분 적재용)
     * 대상 API 가 오래된 순 정렬이므로 마지막 페이지부터 recentPages 페이지만 조회
     * numOfRows = 페이지당 조회 수
     * recentPages = 적재기별 마지막 페이지부터 조회할 페이지 수
     * 최종반환 결과 = 적재기별 결과 목록
     */
    public List<IngestionResult> ingestAllRecent(int numOfRows, int recentPages) {
        log.info("========== 코퍼스 최신 적재 시작 (numOfRows={}, recentPages={}, 적재기={}개) ==========",
                numOfRows, recentPages, ingesters.size());

        List<IngestionResult> results = new ArrayList<>();
        for (CorpusIngester ingester : ingesters) {
            try {
                results.add(ingester.ingestRecent(numOfRows, recentPages));
            } catch (Exception e) {
                // 한 적재기 전체 실패가 다른 적재기를 막지 않도록
                log.error("[{}] 최신 적재기 실행 실패", ingester.apiName(), e);
                results.add(new IngestionResult(ingester.apiName(), 0, 0, 0, 0));
            }
        }

        log.info("========== 코퍼스 최신 적재 종료 ==========");
        results.forEach(r -> log.info("  결과: {}", r));
        return results;
    }
}
