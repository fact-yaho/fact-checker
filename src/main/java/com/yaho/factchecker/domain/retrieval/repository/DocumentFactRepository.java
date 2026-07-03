package com.yaho.factchecker.domain.retrieval.repository;

import com.yaho.factchecker.domain.retrieval.entity.DocumentFact;
import com.yaho.factchecker.domain.retrieval.repository.projection.DocumentVectorScoreProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentFactRepository extends JpaRepository<DocumentFact, UUID> {

    // 특정 근거문서에서 추출된 세부 사실 목록
    List<DocumentFact> findAllByEvidenceDocument_EvidenceDocumentId(UUID evidenceDocumentId);

    /**
     * [PER_CLAIM = fact 유사도 → 문서 MAX 집계]
     * 특정 소주장(claimId)이 수집한 모든 문서의 fact를 입력 벡터와 비교,
     * 문서별로 가장 높은 유사도(MAX)를 문서 점수로 반환 (전수 계산, LIMIT 없음)
     *
     * 전수 계산 = LIMIT 없이 모든 문서 반환 (RRF에서 BM25와 융합 후 Top-K는 service에서)
     * MAX 집계 = GROUP BY + MAX (결정 2-A)
     * fact → evidence_document → (claim_id 필터)
     *
     * claimId = 대상 소주장 id (이 소주장이 수집한 문서들로 범위 한정)
     * queryVector = pgvector 리터럴 문자열 "[v1,v2,...]"
     */
    @Query(value = """
            SELECT
                ed.evidence_document_id AS evidenceDocumentId,
                MAX(1 - (df.fact_vector <=> CAST(:queryVector AS vector))) AS maxSimilarity
            FROM document_fact df
            JOIN evidence_document ed
                ON df.evidence_document_id = ed.evidence_document_id
            WHERE ed.claim_id = :claimId
              AND df.fact_vector IS NOT NULL
            GROUP BY ed.evidence_document_id
            ORDER BY maxSimilarity DESC
            """, nativeQuery = true)
    List<DocumentVectorScoreProjection> findDocumentVectorScores(
            @Param("claimId") UUID claimId,
            @Param("queryVector") String queryVector
    );

    /**
     * [CORPUS = 전체 코퍼스에서 fact 유사도 → 문서 MAX 집계 → Top-K]
     * 소주장과 무관하게 적재된 코퍼스 문서(source_type='CORPUS') 전체의 fact를 입력 벡터와 비교,
     * 문서별 최고 유사도(MAX)를 점수로 하여 상위 K개 문서만 반환
     * per-claim과 달리 후보가 수만 건이므로 LIMIT 필수
     * ORDER BY 의 fact_vector <=> 가 HNSW 인덱스(idx_document_fact_vector, 코사인)를 탐
     *
     * queryVector = pgvector 리터럴 문자열 "[v1,v2,...]"
     * topK = 반환할 상위 문서 수
     */
    @Query(value = """
            SELECT
                ed.evidence_document_id AS evidenceDocumentId,
                MAX(1 - (df.fact_vector <=> CAST(:queryVector AS vector))) AS maxSimilarity
            FROM document_fact df
            JOIN evidence_document ed
                ON df.evidence_document_id = ed.evidence_document_id
            WHERE ed.source_type = 'CORPUS'
              AND df.fact_vector IS NOT NULL
            GROUP BY ed.evidence_document_id
            ORDER BY maxSimilarity DESC
            LIMIT :topK
            """, nativeQuery = true)
    List<DocumentVectorScoreProjection> findCorpusDocumentVectorScores(
            @Param("queryVector") String queryVector,
            @Param("topK") int topK
    );
}
