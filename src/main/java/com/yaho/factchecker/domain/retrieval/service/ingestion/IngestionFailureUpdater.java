package com.yaho.factchecker.domain.retrieval.service.ingestion;

import com.yaho.factchecker.domain.retrieval.entity.IngestionFailure;
import com.yaho.factchecker.domain.retrieval.repository.IngestionFailureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 실패 기록 상태 변경 전용 빈 (짧은 트랜잭션 경계)
 * 재처리 결과(성공/실패)를 실패 기록 하나 단위로 반영
 *
 * (LLM 재처리는 트랜잭션 밖에서 수행하고, 상태 변경 저장만 이 빈의 짧은 트랜잭션으로 처리)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionFailureUpdater {

    private final IngestionFailureRepository ingestionFailureRepository;

    // 재처리 성공 반영 — resolved=true, 재시도 횟수 증가
    @Transactional
    public void markResolved(UUID failureId) {
        IngestionFailure failure = ingestionFailureRepository.findById(failureId).orElse(null);
        if (failure == null) {
            log.warn("[updater] 실패 기록 없음 → 성공 반영 스킵. failureId={}", failureId);
            return;
        }
        failure.markResolved();
        // 영속 상태이므로 트랜잭션 커밋 시 자동 반영 (명시적 save 불필요하나 안전하게 호출)
        ingestionFailureRepository.save(failure);
    }

    // 재처리 실패 반영 — 재시도 횟수 증가 + 사유 갱신
    @Transactional
    public void markRetryFailed(UUID failureId, String reason) {
        IngestionFailure failure = ingestionFailureRepository.findById(failureId).orElse(null);
        if (failure == null) {
            log.warn("[updater] 실패 기록 없음 → 실패 반영 스킵. failureId={}", failureId);
            return;
        }
        failure.markRetryFailed(reason);
        ingestionFailureRepository.save(failure);
    }
}
