package com.yaho.factchecker.domain.retrieval.repository;

import com.yaho.factchecker.domain.retrieval.entity.IngestionFailure;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionFailureRepository extends JpaRepository<IngestionFailure, UUID> {

    // 미해결 실패 기록 조회 (검증·조회용)
    List<IngestionFailure> findAllByResolvedFalse();

    // 미해결 + 재시도 상한 미만 실패 (재처리 대상)
    List<IngestionFailure> findAllByResolvedFalseAndRetryCountLessThan(int maxRetryCount);

    // 미해결이면서 재시도 상한 이상(영구 실패로 보류)인 실패 수 (로그·모니터링용)
    long countByResolvedFalseAndRetryCountGreaterThanEqual(int maxRetryCount);
}
