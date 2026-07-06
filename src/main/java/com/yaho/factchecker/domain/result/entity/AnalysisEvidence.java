package com.yaho.factchecker.domain.result.entity;

import com.yaho.factchecker.domain.result.code.EvidenceSourceType;
import com.yaho.factchecker.global.type.Stance;
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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

// TODO: API내 자료 출처 구분/점수 배점을 위해 구분 테이블 및 필드 추가 고려
@Getter
@Entity
@Table(name = "analysis_evidence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = """
    UPDATE analysis_evidence
    SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
    WHERE id = ?
""")
public class AnalysisEvidence {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * 분석 결과
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private AnalysisResult analysisResult;

    // NOTE : 추후 AiLog 엔티티가 생기면 연관관계로 변경 가능
    /**
     * AI 호출 로그 ID
     */
    @Column(name = "ai_log_id")
    private UUID aiLogId;

    /**
     * 근거 자료 유형
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 50, nullable = false)
    private EvidenceSourceType sourceType;

    /**
     * 근거 자료 제목
     */
    @Column(name = "source_title", columnDefinition = "TEXT", nullable = false)
    private String sourceTitle;

    /**
     * 근거 자료 URL
     */
    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    /**
     * 공공데이터 API ID
     */
    @Column(name = "source_id", length = 100)
    private String sourceId;

    /**
     * 개별 문서에 대한 판단 이유
     */
    @Column(name = "judgment_reason", columnDefinition = "TEXT")
    private String judgmentReason;

    /**
     * 근거 자료 발행 일시
     */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /**
     * 근거로 사용된 핵심 문장
     */
    @Column(name = "snippet", columnDefinition = "TEXT")
    private String snippet;

    /**
     * 주장에 대한 입장
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "stance", length = 30, nullable = false)
    private Stance stance;

    /**
     * 주장과 근거의 관련도 점수
     */
    @Column(name = "relevance_score")
    private Double relevanceScore;

    /**
     * 주장과 근거의 유사도 점수
     */
    @Column(name = "similarity_score")
    private Double similarityScore;

    /**
     * 화면 표시 순서
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Factory Methods ---------------------------------------------------------------
    @Builder
    private AnalysisEvidence(
        AnalysisResult analysisResult,
        UUID aiLogId,
        EvidenceSourceType sourceType,
        String sourceTitle,
        String sourceUrl,
        String sourceId,
        String judgmentReason,
        LocalDateTime publishedAt,
        String snippet,
        Stance stance,
        Double relevanceScore,
        Double similarityScore,
        Integer displayOrder
    ) {
        this.analysisResult = analysisResult;
        this.aiLogId = aiLogId;
        this.sourceType = sourceType;
        this.sourceTitle = sourceTitle;
        this.sourceUrl = sourceUrl;
        this.sourceId = sourceId;
        this.judgmentReason = judgmentReason;
        this.publishedAt = publishedAt;
        this.snippet = snippet;
        this.stance = stance;
        this.relevanceScore = relevanceScore;
        this.similarityScore = similarityScore;
        this.displayOrder = displayOrder;
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

    public void updateDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void updateScores(Double relevanceScore, Double similarityScore) {
        this.relevanceScore = relevanceScore;
        this.similarityScore = similarityScore;
    }

    public void updateJudgment(
        Stance stance,
        String judgmentReason,
        String snippet
    ) {
        this.stance = stance;
        this.judgmentReason = judgmentReason;
        this.snippet = snippet;
    }
}
