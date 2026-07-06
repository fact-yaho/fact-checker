package com.yaho.factchecker.domain.retrieval.repository;

import com.yaho.factchecker.domain.retrieval.entity.IngestionFailure;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionFailureRepository extends JpaRepository<IngestionFailure, UUID> {

    // 미해결 실패 기록 조회 (재처리 대상)
    List<IngestionFailure> findAllByResolvedFalse();
}
