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
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Table(
    name = "score_breakdown",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_score_breakdown_claim_analysis_result",
            columnNames = "claim_analysis_result_id"
        )
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
     * 소주장별 분석 결과
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_analysis_result_id", nullable = false)
    private ClaimAnalysisResult claimAnalysisResult;

    @Column(name = "official_consistency_score", nullable = false)
    private Double officialConsistencyScore;

    @Column(name = "evidence_relevance_score", nullable = false)
    private Double evidenceRelevanceScore;

    @Column(name = "evidence_sufficiency_score", nullable = false)
    private Double evidenceSufficiencyScore;

    @Column(name = "recency_score", nullable = false)
    private Double recencyScore;

    @Column(name = "contradiction_penalty", nullable = false)
    private Double contradictionPenalty;

    /**
     * 소주장별 최종 점수
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
        ClaimAnalysisResult claimAnalysisResult,
        Double officialConsistencyScore,
        Double evidenceRelevanceScore,
        Double evidenceSufficiencyScore,
        Double recencyScore,
        Double contradictionPenalty,
        Double finalScore
    ) {
        this.claimAnalysisResult = claimAnalysisResult;
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
