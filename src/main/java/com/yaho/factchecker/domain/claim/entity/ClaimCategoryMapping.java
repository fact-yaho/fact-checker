package com.yaho.factchecker.domain.claim.entity;

import com.yaho.factchecker.domain.retrieval.entity.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
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

@Entity
@Getter
@Table(name = "claim_category")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimCategoryMapping {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "claim_category_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", columnDefinition = "uuid", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", columnDefinition = "uuid", nullable = false)
    private Category category;

    @Column(name = "primary_category", nullable = false)
    private boolean primaryCategory;

    @Builder
    private ClaimCategoryMapping(Category category, boolean primaryCategory) {
        this.category = category;
        this.primaryCategory = primaryCategory;
    }

    void assignClaim(Claim claim) {
        this.claim = claim;
    }
}
