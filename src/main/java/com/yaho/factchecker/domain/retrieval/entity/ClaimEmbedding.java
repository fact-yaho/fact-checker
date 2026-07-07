package com.yaho.factchecker.domain.retrieval.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "claim_embedding")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimEmbedding {
    @Id
    @UuidGenerator
    @Column(name = "claim_embedding_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID claimEmbeddingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", columnDefinition = "uuid", nullable = false)
    private Category category;

    // FK
    @Column(name = "claim_id", columnDefinition = "uuid", nullable = false)
    private UUID claimId;

    @Column(name = "claim_text", columnDefinition = "TEXT")
    private String claimText;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "claim_vector", columnDefinition = "vector(1536)")
    private float[] claimVector;

    @Builder
    public ClaimEmbedding(Category category, UUID claimId, String claimText, float[] claimVector) {
        this.category = category;
        this.claimId = claimId;
        this.claimText = claimText;
        this.claimVector = claimVector;
    }
}
