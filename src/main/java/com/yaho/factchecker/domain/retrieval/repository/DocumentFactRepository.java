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
     * 1단계(서브쿼리 top_facts): 코퍼스 fact 중 쿼리 벡터에 가장 가까운 fact를 factLimit개 조회 (ANN)
     *   - 연도 필터를 여기 넣지 않음 → ANN 후보가 연도 때문에 조기 소진되어 recall이 떨어지는 것 방지
     * 2단계(중간 조인): top_facts 를 evidence_document와 조인하며 연도 필터 적용 (ANN 이후 필터링)
     *   - EXTRACT(YEAR ...) 대신 published_at 범위 비교 → 함수 래핑 없이 인덱스 친화적
     *   - fromYear → make_date(fromYear,1,1) 이상 / toYear → make_date(toYear+1,1,1) 미만 (연도 전체 포함)
     *   - published_at IS NULL 문서는 연도 조건과 무관하게 항상 포함(recall 우선)
     * 3단계(바깥): 문서별 MAX 유사도로 집계 → 상위 topK 문서
     *
     * 근사(approximate) 특성: ANN 후보(factLimit) 중 연도 매칭분만 남으므로, 연도가 희소하면
     * 최종 문서가 topK 보다 적을 수 있음, 이 경우 factLimit을 키워야 함(현재 topK=5 → factLimit=100).
     *
     * queryVector = pgvector 리터럴 "[v1,v2,...]"
     * factLimit    = ANN 1단계에서 뽑을 근접 fact 후보 수
     * topK         = 최종 반환할 상위 문서 수
     * fromYear     = 연도 하한 (inclusive, null = 하한 없음)
     * toYear       = 연도 상한 (inclusive, null = 상한 없음)
     */
    @Query(value = """
            SELECT
                filtered.evidence_document_id AS evidenceDocumentId,
                MAX(1 - (filtered.fact_vector <=> CAST(:queryVector AS vector))) AS maxSimilarity
            FROM (
                SELECT top_facts.evidence_document_id AS evidence_document_id,
                       top_facts.fact_vector AS fact_vector
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
                JOIN evidence_document ed2
                    ON top_facts.evidence_document_id = ed2.evidence_document_id
                WHERE ed2.published_at IS NULL
                   OR (
                       (CAST(:fromYear AS integer) IS NULL
                           OR ed2.published_at >= make_date(:fromYear, 1, 1))
                       AND (CAST(:toYear AS integer) IS NULL
                           OR ed2.published_at < make_date(:toYear + 1, 1, 1))
                   )
            ) AS filtered
            GROUP BY filtered.evidence_document_id
            ORDER BY maxSimilarity DESC
            LIMIT :topK
            """, nativeQuery = true)
    List<DocumentVectorScoreProjection> findCorpusDocumentVectorScores(
            @Param("queryVector") String queryVector,
            @Param("factLimit") int factLimit,
            @Param("topK") int topK,
            @Param("fromYear") Integer fromYear,
            @Param("toYear") Integer toYear
    );
}
