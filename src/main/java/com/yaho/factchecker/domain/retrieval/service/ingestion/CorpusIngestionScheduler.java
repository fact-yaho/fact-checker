package com.yaho.factchecker.domain.retrieval.service.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * < 코퍼스 적재 스케줄러 >
 * 매일 새벽 4시에 최신 문서를 증분 적재하고, 적재 실패분을 재처리
 *
 * - 적재: 검색 미지원 API의 최신 문서 쪽(마지막 페이지부터 recentPages 페이지)만 조회 → 중복은 스킵
 * - 재처리: ingestion_failure의 미해결 실패를 재처리
 *
 * 수동 트리거(CorpusIngestionService.ingestAll/ingestAllRecent, IngestionRetryService.retryFailures)는 유지
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CorpusIngestionScheduler {

    private final CorpusIngestionService corpusIngestionService;
    private final IngestionRetryService ingestionRetryService;

    // 스케줄러 적재 파라미터 (증분이라 페이지당 넉넉히 + 뒤 몇 페이지만)
    private static final int SCHEDULED_NUM_OF_ROWS = 100;
    private static final int SCHEDULED_RECENT_PAGES = 3;

    /**
     * 매일 새벽 4시 (초 분 시 일 월 요일)
     * 1) 최신 문서 증분 적재 → 2) 실패분 재처리
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void scheduledIngestAndRetry() {
        log.info("########## [스케줄러] 코퍼스 증분 적재 + 재처리 시작 ##########");

        // [1단계] 최신 문서 증분 적재 (마지막 페이지부터 recentPages)
        try {
            corpusIngestionService.ingestAllRecent(SCHEDULED_NUM_OF_ROWS, SCHEDULED_RECENT_PAGES);
        } catch (Exception e) {
            log.error("[스케줄러] 증분 적재 중 예외", e);
        }

        // [2단계] 적재 실패분 재처리 (일시적 오류로 실패한 문서 복구)
        try {
            IngestionRetryService.RetryResult retryResult = ingestionRetryService.retryFailures();
            log.info("[스케줄러] 재처리 결과 - {}", retryResult);
        } catch (Exception e) {
            log.error("[스케줄러] 재처리 중 예외", e);
        }

        log.info("########## [스케줄러] 코퍼스 증분 적재 + 재처리 종료 ##########");
    }
}
