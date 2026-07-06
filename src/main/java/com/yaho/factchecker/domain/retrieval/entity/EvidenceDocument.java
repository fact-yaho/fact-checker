package com.yaho.factchecker.domain.retrieval.entity;

import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.global.type.SourceType;
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
@Table(name = "evidence_document")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceDocument {

    @Id
    @UuidGenerator
    @Column(name = "evidence_document_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID evidenceDocumentId;

    // FK, claim 테이블
    @Column(name = "claim_id", columnDefinition = "uuid")
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    @Column(name = "api_name", length = 255)
    private String apiName;

    @Column(name = "search_keyword", length = 255)
    private String searchKeyword;

    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "content_cleaned", columnDefinition = "TEXT")
    private String contentCleaned;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_name")
    private ClaimCategory categoryName;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "original_url", columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "author_or_dept", length = 255)
    private String authorOrDept;

    @Builder
    public EvidenceDocument(UUID claimId, SourceType sourceType, String apiName, String searchKeyword,
                            String title, String contentCleaned, ClaimCategory categoryName,
                            LocalDateTime publishedAt, String originalUrl, String authorOrDept) {
        this.claimId = claimId;
        // 미지정 시 PER_CLAIM 기본값
        this.sourceType = (sourceType != null) ? sourceType : SourceType.PER_CLAIM;
        this.apiName = apiName;
        this.searchKeyword = searchKeyword;
        this.title = title;
        this.contentCleaned = contentCleaned;
        this.categoryName = categoryName;
        this.publishedAt = publishedAt;
        this.originalUrl = originalUrl;
        this.authorOrDept = authorOrDept;
    }
}
