package com.yaho.factchecker.domain.retrieval.repository;

import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.global.type.SourceType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceDocumentRepository extends JpaRepository<EvidenceDocument, UUID> {

    List<EvidenceDocument> findAllByClaimId(UUID claimId);
    // 코퍼스 중복 적재 체크 — 같은 source_type + content_hash 문서 존재 여부
    boolean existsBySourceTypeAndContentHash(SourceType sourceType, String contentHash);
}
