package com.yaho.factchecker.domain.retrieval.entity;

import com.yaho.factchecker.global.type.IngestionFailureStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Table(name = "ingestion_failure")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestionFailure {

    @Id
    @UuidGenerator
    @Column(name = "ingestion_failure_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID ingestionFailureId;

    // 어느 API 의 문서인지 (담당 적재기 식별 + 재처리 시 item 타입 판단)
    @Column(name = "api_name", length = 255, nullable = false)
    private String apiName;

    // 원본 item JSON 스냅샷 (재처리 시 이 JSON을 item 으로 복원)
    @Column(name = "item_json", columnDefinition = "TEXT", nullable = false)
    private String itemJson;

    // 실패 단계
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_stage", length = 20, nullable = false)
    private IngestionFailureStage failureStage;

    // 실패 사유 (예외 메시지)
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    // 실패 시각
    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    // 재시도 횟수 (0 부터)
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    // 최종 성공 여부 (재처리 성공 시 true)
    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Builder
    public IngestionFailure(String apiName, String itemJson,
                            IngestionFailureStage failureStage, String failureReason,
                            LocalDateTime failedAt) {
        this.apiName = apiName;
        this.itemJson = itemJson;
        this.failureStage = failureStage;
        this.failureReason = failureReason;
        // 미지정 시 현재 시각
        this.failedAt = (failedAt != null) ? failedAt : LocalDateTime.now();
        this.retryCount = 0;
        this.resolved = false;
    }

    // 재처리 실패 시 — 재시도 횟수 증가 + 사유 갱신
    public void markRetryFailed(String reason) {
        this.retryCount++;
        this.failureReason = reason;
    }

    // 재처리 성공 시 — 최종 성공 표시 (재시도 횟수도 증가)
    public void markResolved() {
        this.retryCount++;
        this.resolved = true;
    }
}
