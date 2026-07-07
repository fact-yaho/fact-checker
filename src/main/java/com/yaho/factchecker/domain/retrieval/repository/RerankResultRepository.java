package com.yaho.factchecker.domain.retrieval.repository;

import com.yaho.factchecker.domain.retrieval.entity.RerankResult;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RerankResultRepository extends JpaRepository<RerankResult, UUID> {

    // 특정 소주장의 재정렬 결과를 최종 순위 오름차순으로 (Top-K 추출용)
    List<RerankResult> findAllByClaimIdOrderByFinalRankAsc(UUID claimId);

    // 재계산 시 기존 결과 덮어쓰기를 위한 hard delete (추후 BaseEntity 상속하면 softDelete로)
    void deleteByClaimId(UUID claimId);

    /**
     * [캐시 조회] 후보 소주장들 중 캐시 재사용 가능한 claim_id 집합을 반환
     * 조건: candidateClaimIds 중에서
     *   - 연도 범위가 요청과 정확히 일치 (null == null 포함, null-safe)
     *   - created_at이 유효기간 기준시각(cutoff) 이후 (= 최근 결과)
     * 반환: 조건을 통과한 claim_id 목록 (DISTINCT — rerank_result는 소주장당 여러 행이므로)
     *
     * 유사도 최고 후보 선택은 service에서 (findSimilarClaims의 유사도 순서로 이 집합을 훑음)
     *
     * candidateClaimIds = findSimilarClaims 로 얻은 유사(≥임계값) 소주장 id 들
     * fromYear/toYear    = 요청의 연도 범위 (null 가능)
     * cutoff             = 유효기간 기준시각 (now - 2일). 이보다 created_at 이 최신이어야 유효
     */
    @Query("""
            SELECT DISTINCT r.claimId
            FROM RerankResult r
            WHERE r.claimId IN :candidateClaimIds
              AND r.createdAt >= :cutoff
              AND (
                    (r.fromYear = :fromYear) OR (r.fromYear IS NULL AND :fromYear IS NULL)
                  )
              AND (
                    (r.toYear = :toYear) OR (r.toYear IS NULL AND :toYear IS NULL)
                  )
            """)
    List<UUID> findCacheableClaimIds(
            @Param("candidateClaimIds") Collection<UUID> candidateClaimIds,
            @Param("fromYear") Integer fromYear,
            @Param("toYear") Integer toYear,
            @Param("cutoff") LocalDateTime cutoff
    );
}
