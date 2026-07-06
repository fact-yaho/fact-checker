package com.yaho.factchecker.domain.retrieval.service.ingestion;

import com.yaho.factchecker.domain.retrieval.entity.IngestionFailure;
import com.yaho.factchecker.domain.retrieval.repository.IngestionFailureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 코퍼스 적재 실패 재처리 서비스
 * 미해결(resolved=false) 실패 기록을 조회하여,
 * 각 기록의 apiName 으로 담당 적재기를 찾아 원본 item JSON 을 복원·재처리,
 * 성공 시 resolved 표시하고, 실패 시 재시도 횟수 증가
 *
 * LLM 재처리(retryOne)는 트랜잭션 밖에서 수행하고, 실패 기록 상태 변경만
 * IngestionFailureUpdater(짧은 트랜잭션)에 위임
 * (자동 트리거(스케줄러)는 추후 작업, 지금은 수동 호출 진입점)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionRetryService {

    private final IngestionFailureRepository ingestionFailureRepository;
    private final IngestionFailureUpdater failureUpdater;
    // 스프링이 CorpusIngester 구현체(브리핑·연설문 등)를 전부 주입
    private final List<CorpusIngester> ingesters;

    /**
     * 미해결 실패 기록을 모두 재처리
     * 반환 = 재처리 결과 요약 (시도/성공/실패 건수)
     */
    public RetryResult retryFailures() {
        // apiName → 적재기 매핑 (한 번만 구성)
        Map<String, CorpusIngester> ingesterByApi = ingesters.stream()
                .collect(Collectors.toMap(CorpusIngester::apiName, Function.identity()));

        List<IngestionFailure> failures = ingestionFailureRepository.findAllByResolvedFalse();
        log.info("========== 실패 재처리 시작 (대상 {}건) ==========", failures.size());

        int attempted = 0, resolved = 0, stillFailed = 0, skipped = 0;

        for (IngestionFailure failure : failures) {
            CorpusIngester ingester = ingesterByApi.get(failure.getApiName());

            // 담당 적재기가 없으면 스킵 (해당 API 적재기가 아직 없거나 제거됨)
            if (ingester == null) {
                skipped++;
                log.warn("[재처리] 담당 적재기 없음 → 스킵. apiName={}, failureId={}",
                        failure.getApiName(), failure.getIngestionFailureId());
                continue;
            }

            attempted++;
            try {
                // LLM 재처리 — 트랜잭션 밖 (문서 저장은 DocumentPersister 트랜잭션)
                int facts = ingester.retryOne(failure.getItemJson());
                // 성공 반영 — 짧은 트랜잭션
                failureUpdater.markResolved(failure.getIngestionFailureId());
                resolved++;
                log.info("[재처리] 성공. apiName={}, fact {}개, failureId={}",
                        failure.getApiName(), facts, failure.getIngestionFailureId());
            } catch (Exception e) {
                // 실패 반영 — 짧은 트랜잭션
                failureUpdater.markRetryFailed(failure.getIngestionFailureId(), e.getMessage());
                stillFailed++;
                log.error("[재처리] 실패. apiName={}, failureId={}: {}",
                        failure.getApiName(), failure.getIngestionFailureId(), e.getMessage());
            }
        }

        RetryResult result = new RetryResult(failures.size(), attempted, resolved, stillFailed, skipped);
        log.info("========== 실패 재처리 종료 - {} ==========", result);
        return result;
    }

    /**
     * 재처리 결과 요약
     * total = 조회된 미해결 실패 수
     * attempted = 재처리 시도 수 (담당 적재기 있는 것)
     * resolved = 재처리 성공 수
     * stillFailed = 재처리했으나 또 실패한 수
     * skipped = 담당 적재기 없어 스킵한 수
     */
    public record RetryResult(int total, int attempted, int resolved, int stillFailed, int skipped) {}
}
