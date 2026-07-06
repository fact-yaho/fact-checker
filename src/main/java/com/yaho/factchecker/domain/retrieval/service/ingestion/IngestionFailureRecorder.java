package com.yaho.factchecker.domain.retrieval.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaho.factchecker.domain.retrieval.entity.IngestionFailure;
import com.yaho.factchecker.domain.retrieval.repository.IngestionFailureRepository;
import com.yaho.factchecker.global.type.IngestionFailureStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 코퍼스 적재 실패 기록 저장 빈 (짧은 트랜잭션 경계)
 * 실패한 원본 item을 JSON 으로 직렬화하여 ingestion_failure에 저장
 *
 * (적재 본류 흐름과 분리된 별도 빈 — 실패 기록 저장이 적재 트랜잭션에 얽히지 않도록)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionFailureRecorder {

    private final IngestionFailureRepository ingestionFailureRepository;
    private final ObjectMapper objectMapper;

    /**
     * 실패 기록 저장
     * apiName = 어느 API (재처리 시 담당 적재기/타입 판단)
     * item = 실패한 원본 item 객체 (JSON 직렬화하여 스냅샷 저장), null 이면 빈 JSON
     * stage = 실패 단계 (FETCH/CLEAN/WRITE)
     * reason = 실패 사유 (예외 메시지)
     */
    @Transactional
    public void record(String apiName, Object item, IngestionFailureStage stage, String reason) {
        String itemJson = serialize(item);

        IngestionFailure failure = IngestionFailure.builder()
                .apiName(apiName)
                .itemJson(itemJson)
                .failureStage(stage)
                .failureReason(reason)
                .failedAt(LocalDateTime.now())
                .build();
        ingestionFailureRepository.save(failure);
    }

    // item → JSON 직렬화, 직렬화 자체가 실패하면 빈 JSON 으로 대체(기록 자체는 남기기 위해)
    private String serialize(Object item) {
        if (item == null) return "{}";
        try {
            return objectMapper.writeValueAsString(item);
        } catch (Exception e) {
            log.warn("[failureRecorder] item JSON 직렬화 실패 → 빈 JSON 으로 대체: {}", e.getMessage());
            return "{}";
        }
    }
}
