package com.yaho.factchecker.domain.retrieval.entity;

import com.yaho.factchecker.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Table(name = "rerank_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RerankResult extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "rerank_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID rerankId;

    // FK
    @Column(name = "claim_id", columnDefinition = "uuid", nullable = false)
    private UUID claimId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_document_id", columnDefinition = "uuid", nullable = false)
    private EvidenceDocument evidenceDocument;

    @Column(name = "bm25_rank", nullable = false)
    private Integer bm25Rank;

    @Column(name = "bm25_score", nullable = false)
    private Double bm25Score;

    @Column(name = "vector_sim_rank", nullable = false)
    private Integer vectorSimRank;

    @Column(name = "vector_sim_score", nullable = false)
    private Double vectorSimScore;

    @Column(name = "final_score", nullable = false)
    private Double finalScore;

    @Column(name = "final_rank", nullable = false)
    private Integer finalRank;

    // 캐시 키 — 이 결과가 검색된 연도 범위 (연도 없는 소주장은 null, null)
    @Column(name = "from_year")
    private Integer fromYear;

    @Column(name = "to_year")
    private Integer toYear;

    @Builder
    public RerankResult(UUID claimId, EvidenceDocument evidenceDocument, Integer bm25Rank,
                        Double bm25Score, Integer vectorSimRank, Double vectorSimScore,
                        Double finalScore, Integer finalRank, Integer fromYear, Integer toYear) {
        this.claimId = claimId;
        this.evidenceDocument = evidenceDocument;
        this.bm25Rank = bm25Rank;
        this.bm25Score = bm25Score;
        this.vectorSimRank = vectorSimRank;
        this.vectorSimScore = vectorSimScore;
        this.finalScore = finalScore;
        this.finalRank = finalRank;
        this.fromYear = fromYear;
        this.toYear = toYear;
    }
}
