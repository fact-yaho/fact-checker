package com.yaho.factchecker.domain.result.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Table(
    name = "score_breakdown",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_score_breakdown_analysis_result", columnNames = "analysis_result_id")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = """
    UPDATE score_breakdown
    SET deleted_at = CURRENT_TIMESTAMP,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = ?
""")
public class ScoreBreakdown {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * 분석 결과
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private AnalysisResult analysisResult;

    /**
     * 공식 입장과의 일치도 점수
     */
    @Column(name = "official_consistency_score", nullable = false)
    private Double officialConsistencyScore;

    /**
     * 근거 자료의 관련성 점수
     */
    @Column(name = "evidence_relevance_score", nullable = false)
    private Double evidenceRelevanceScore;

    /**
     * 근거 자료의 충분성 점수
     *
     * 이미지에서는 evedence_sufficiency_score로 보이는데,
     * 오타라면 evidence_sufficiency_score로 수정하는 것을 추천합니다.
     */
    @Column(name = "evidence_sufficiency_score", nullable = false)
    private Double evidenceSufficiencyScore;

    /**
     * 근거 자료의 최신성 점수
     */
    @Column(name = "recency_score", nullable = false)
    private Double recencyScore;

    /**
     * 반박 근거에 따른 감점
     */
    @Column(name = "contradiction_penalty", nullable = false)
    private Double contradictionPenalty;

    /**
     * 최종 신뢰도 점수
     */
    @Column(name = "final_score", nullable = false)
    private Double finalScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private ScoreBreakdown(
        AnalysisResult analysisResult,
        Double officialConsistencyScore,
        Double evidenceRelevanceScore,
        Double evidenceSufficiencyScore,
        Double recencyScore,
        Double contradictionPenalty,
        Double finalScore
    ) {
        this.analysisResult = analysisResult;
        this.officialConsistencyScore = officialConsistencyScore;
        this.evidenceRelevanceScore = evidenceRelevanceScore;
        this.evidenceSufficiencyScore = evidenceSufficiencyScore;
        this.recencyScore = recencyScore;
        this.contradictionPenalty = contradictionPenalty;
        this.finalScore = finalScore;
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

    public void updateScores(
        Double officialConsistencyScore,
        Double evidenceRelevanceScore,
        Double evidenceSufficiencyScore,
        Double recencyScore,
        Double contradictionPenalty,
        Double finalScore
    ) {
        this.officialConsistencyScore = officialConsistencyScore;
        this.evidenceRelevanceScore = evidenceRelevanceScore;
        this.evidenceSufficiencyScore = evidenceSufficiencyScore;
        this.recencyScore = recencyScore;
        this.contradictionPenalty = contradictionPenalty;
        this.finalScore = finalScore;
    }
}
