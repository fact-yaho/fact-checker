package com.yaho.factchecker.domain.result.entity;

import com.yaho.factchecker.global.type.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Table(name = "claim_analysis_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = """
    UPDATE claim_analysis_result
    SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
    WHERE id = ?
""")
public class ClaimAnalysisResult {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * 사용자 입력 전체에 대한 분석 결과
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private AnalysisResult analysisResult;

    /**
     * 소주장 ID
     */
    @Column(name = "claim_id", columnDefinition = "uuid")
    private UUID claimId;

    /**
     * 분리된 소주장 텍스트
     */
    @Column(name = "claim_text", columnDefinition = "TEXT", nullable = false)
    private String claimText;

    /**
     * 소주장 표시 순서
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    /**
     * 소주장별 최종 점수
     */
    @Column(name = "final_score", nullable = false)
    private Double finalScore;

    /**
     * 소주장별 최종 판정
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", length = 30, nullable = false)
    private Verdict verdict;

    /**
     * 소주장별 분석 요약
     */
    @Column(name = "answer_summary", columnDefinition = "TEXT", nullable = false)
    private String answerSummary;

    /**
     * 소주장별 상세 설명
     */
    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private ClaimAnalysisResult(
        AnalysisResult analysisResult,
        UUID claimId,
        String claimText,
        Integer displayOrder,
        Double finalScore,
        Verdict verdict,
        String answerSummary,
        String explanation
    ) {
        this.analysisResult = analysisResult;
        this.claimId = claimId;
        this.claimText = claimText;
        this.displayOrder = displayOrder;
        this.finalScore = finalScore;
        this.verdict = verdict;
        this.answerSummary = answerSummary;
        this.explanation = explanation;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateResult(
        Double finalScore,
        Verdict verdict,
        String answerSummary,
        String explanation
    ) {
        this.finalScore = finalScore;
        this.verdict = verdict;
        this.answerSummary = answerSummary;
        this.explanation = explanation;
    }
}
