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
     * [PER_CLAIM: fact 유사도 → 문서 MAX 집계]
     * 특정 소주장(claimId)이 수집한 모든 문서의 fact를 입력 벡터와 비교
     * 문서별로 가장 높은 유사도(MAX)를 문서 점수로 반환 (후보가 적어 전수 계산, LIMIT 없음)
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
     * [CORPUS: 전체 코퍼스에서 벡터 검색 → 문서 Top-K]
     *
     * 1단계(서브쿼리): 코퍼스 fact 중 쿼리 벡터에 가장 가까운 fact 를 factLimit 개만 조회
     *   - ORDER BY fact_vector <=> ... LIMIT 형태라 HNSW 인덱스(idx_document_fact_vector, 코사인)를 탐(ANN)
     *   - 코퍼스 전체 fact 를 훑지 않고 근접 후보만 빠르게 확보
     * 2단계(바깥): 좁혀진 fact 후보만 문서별 MAX 유사도로 집계 → 상위 topK 문서
     *
     * 근사(approximate) 특성: 한 문서의 최고 fact 가 factLimit 밖이면 누락될 수 있으므로
     * factLimit 은 topK 대비 넉넉히(예: topK=5 → factLimit=100~200) 준다
     *
     * queryVector = pgvector 리터럴 "[v1,v2,...]"
     * factLimit    = 1단계에서 좁힐 근접 fact 후보 수
     * topK         = 최종 반환할 상위 문서 수
     */
    @Query(value = """
            SELECT
                top_facts.evidence_document_id AS evidenceDocumentId,
                MAX(1 - (top_facts.fact_vector <=> CAST(:queryVector AS vector))) AS maxSimilarity
            FROM (
                SELECT df.evidence_document_id AS evidence_document_id,
                       df.fact_vector AS fact_vector
                FROM document_fact df
                JOIN evidence_document ed
                    ON df.evidence_document_id = ed.evidence_document_id
                WHERE ed.source_type = 'CORPUS'
                  AND df.fact_vector IS NOT NULL
                ORDER BY df.fact_vector <=> CAST(:queryVector AS vector)
                LIMIT :factLimit
            ) AS top_facts
            GROUP BY top_facts.evidence_document_id
            ORDER BY maxSimilarity DESC
            LIMIT :topK
            """, nativeQuery = true)
    List<DocumentVectorScoreProjection> findCorpusDocumentVectorScores(
            @Param("queryVector") String queryVector,
            @Param("factLimit") int factLimit,
            @Param("topK") int topK
    );
}
