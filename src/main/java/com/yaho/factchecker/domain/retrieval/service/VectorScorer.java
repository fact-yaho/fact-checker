package com.yaho.factchecker.domain.retrieval.service;

import com.yaho.factchecker.domain.retrieval.dto.VectorResult;
import com.yaho.factchecker.domain.retrieval.repository.DocumentFactRepository;
import com.yaho.factchecker.domain.retrieval.repository.projection.DocumentVectorScoreProjection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * < 벡터 유사도 점수/순위 계산 오케스트레이터 >
 *
 * - score() = per-claim 문서만 대상 (claim_id 로 좁힘)
 * - scoreHybrid() = per-claim + 코퍼스 문서를 합쳐 유사도순으로 rank 재계산
 *
 * queryVector = pgvector 리터럴 문자열 "[v1,v2,...]"
 * 반환 = 문서별 벡터 결과 (유사도 내림차순, rank 1부터)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorScorer {

    private final DocumentFactRepository documentFactRepository;

    // 한 소주장이 수집한 문서들의 벡터 점수/순위
    public List<VectorResult> score(UUID claimId, String queryVector) {
        if (claimId == null || queryVector == null || queryVector.isBlank()) {
            return Collections.emptyList();
        }

        List<DocumentVectorScoreProjection> projections =
                documentFactRepository.findDocumentVectorScores(claimId, queryVector);

        return toRankedResults(projections);
    }

    /**
     * < 하이브리드 방식 >
     * = per-claim 문서 벡터 + 코퍼스 문서 벡터를 합쳐 유사도순 rank 재계산
     *
     * claim_id로 좁힌 문서와 corpus(source_type='CORPUS' 전체에서 top-k)를 각각 조회한 뒤,
     * 같은 문서가 양쪽에 나오면 더 높은 유사도로 병합하고, 전체를 유사도 내림차순으로 rank 를 다시 매김
     * (두 소스를 그냥 이으면 rank가 각각 1부터라 중복되므로 반드시 재계산)
     *
     * + fromYear/toYear = 코퍼스 연도 필터 (inclusive, null = 해당 방향 무제한)
     *   per-claim 문서는 수집 시점에 이미 범위가 정해지므로 코퍼스 조회에만 전달
     *
     * claimId = 대상 소주장 (per-claim 문서 범위 한정)
     * queryVector = 쿼리 벡터 리터럴
     * corpusTopK = 코퍼스에서 가져올 상위 문서 수
     * corpusFactLimit = 코퍼스 1단계에서 좁힐 근접 fact 후보 수
     * fromYear = 코퍼스 연도 하한 (inclusive, null = 하한 없음)
     * toYear = 코퍼스 연도 상한 (inclusive, null = 상한 없음)
     */
    public List<VectorResult> scoreHybrid(UUID claimId, String queryVector,
                                          int corpusTopK, int corpusFactLimit,
                                          Integer fromYear, Integer toYear) {
        if (queryVector == null || queryVector.isBlank()) {
            return Collections.emptyList();
        }

        // [1단계] claim_id로 집힌 문서의 벡터 점수
        List<DocumentVectorScoreProjection> perClaim = (claimId != null)
                ? documentFactRepository.findDocumentVectorScores(claimId, queryVector)
                : Collections.emptyList();

        // [2단계] 코퍼스 문서 벡터 점수 (source_type='CORPUS' 전체에서 top-k, 연도 필터 적용)
        List<DocumentVectorScoreProjection> corpus =
                documentFactRepository.findCorpusDocumentVectorScores(
                        queryVector, corpusFactLimit, corpusTopK, fromYear, toYear);

        // [3단계] 병합 (문서 중복 시 더 높은 유사도 유지)
        Map<UUID, Double> merged = new LinkedHashMap<>();
        mergeMax(merged, perClaim);
        mergeMax(merged, corpus);

        // [4단계] 유사도 내림차순 정렬 + rank 재부여
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(merged.entrySet());
        sorted.sort(Map.Entry.<UUID, Double>comparingByValue().reversed());

        List<VectorResult> results = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            results.add(new VectorResult(sorted.get(i).getKey(), sorted.get(i).getValue(), i + 1));
        }
        return results;
    }

    // projection 목록 → 유사도 내림차순 rank 부여
    private List<VectorResult> toRankedResults(List<DocumentVectorScoreProjection> projections) {
        List<DocumentVectorScoreProjection> sorted = new ArrayList<>(projections);
        sorted.sort(Comparator.comparingDouble(
                DocumentVectorScoreProjection::getMaxSimilarity).reversed());

        List<VectorResult> results = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            DocumentVectorScoreProjection p = sorted.get(i);
            results.add(new VectorResult(p.getEvidenceDocumentId(), p.getMaxSimilarity(), i + 1));
        }
        return results;
    }

    // 같은 문서가 이미 있으면 더 높은 유사도로 유지
    private void mergeMax(Map<UUID, Double> merged, List<DocumentVectorScoreProjection> src) {
        for (DocumentVectorScoreProjection p : src) {
            merged.merge(p.getEvidenceDocumentId(), p.getMaxSimilarity(), Math::max);
        }
    }
}
