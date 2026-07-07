package com.yaho.factchecker.domain.retrieval.service;

import com.yaho.factchecker.domain.retrieval.dto.Bm25Result;
import com.yaho.factchecker.domain.retrieval.dto.ClaimRetrievalRequest;
import com.yaho.factchecker.domain.retrieval.dto.RerankRow;
import com.yaho.factchecker.domain.retrieval.dto.RetrievedEvidence;
import com.yaho.factchecker.domain.retrieval.dto.VectorResult;
import com.yaho.factchecker.domain.retrieval.entity.Category;
import com.yaho.factchecker.domain.retrieval.entity.ClaimEmbedding;
import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.domain.retrieval.entity.RerankResult;
import com.yaho.factchecker.domain.retrieval.repository.CategoryRepository;
import com.yaho.factchecker.domain.retrieval.repository.ClaimEmbeddingRepository;
import com.yaho.factchecker.domain.retrieval.repository.EvidenceDocumentRepository;
import com.yaho.factchecker.domain.retrieval.repository.RerankResultRepository;
import com.yaho.factchecker.domain.retrieval.repository.projection.SimilarClaimProjection;
import com.yaho.factchecker.global.util.VectorUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * < 근거 탐색 오케스트레이터 >
 *
 * [제공 기능]
 * 1. 소주장 하나에 대한 캐싱 조회 (유사 소주장 + 연도범위 일치 + 유효기간 내 → 기존 결과 재사용)
 * 2. 소주장 하나에 대한 상위 K개의 근거 문서를 제공 (claim_id로 좁힌 문서들 + 코퍼스)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final EvidenceDocumentRepository evidenceDocumentRepository;
    private final RerankResultRepository rerankResultRepository;
    private final ClaimEmbeddingRepository claimEmbeddingRepository;
    private final CategoryRepository categoryRepository;
    private final DocumentCollector documentCollector;
    private final TextEmbedder textEmbedder;
    private final VectorScorer vectorScorer;
    private final Bm25Scorer bm25Scorer;
    private final RrfFusion rrfFusion;

    // Top-K = stance로 넘길 상위 문서 개수
    private static final int TOP_K = 5;

    // 코퍼스에서 후보로 가져올 상위 문서 수
    private static final int CORPUS_TOP_K = 15;
    // 1단계 fact 후보 수
    private static final int CORPUS_FACT_LIMIT = 100;

    // [캐시] 유사 소주장 후보 조회 수
    private static final int SIMILAR_CLAIM_LIMIT = 5;
    // [캐시] 동일 질문으로 볼 최소 코사인 유사도
    private static final double SIMILARITY_THRESHOLD = 0.9;
    // [캐시] 결과 유효기간 (일) — 이보다 오래된 결과는 재검색
    private static final long CACHE_VALID_DAYS = 2;

    // 소주장 1개에 대한 상위 근거문서 K개를 얻는 공개 진입점
    @Transactional
    public List<RetrievedEvidence> retrieveEvidence(ClaimRetrievalRequest request) {
        // [0단계] 검증 불가 소주장은 skip
        if (request == null || !request.isVerifiable()) {
            log.info("검증 불가 또는 빈 요청 → 근거검색 스킵. claimId={}",
                    request != null ? request.claimId() : null);
            return Collections.emptyList();
        }

        UUID claimId = request.claimId();

        // [1단계] 소주장 임베딩 (캐시 조회·벡터검색·임베딩 저장에 재사용)
        float[] queryEmbedding = textEmbedder.embed(request.canonicalClaim());
        String queryVector = VectorUtils.toVectorLiteral(queryEmbedding);

        // [2단계] 캐시 조회 — 유사 소주장 + 연도범위 일치 + 유효기간 내면 기존 결과 재사용
        UUID cacheHitClaimId = findCacheHit(request, queryVector);
        if (cacheHitClaimId != null) {
            log.info("캐시 히트 → 기존 결과 재사용. 요청claimId={}, 재사용claimId={}", claimId, cacheHitClaimId);
            return getTopKDocuments(cacheHitClaimId);
        }

        // === 캐시 미스 → 실제 검색 ===

        // [3단계] 소주장에 대한 근거문서 수집·저장 (per-claim), fact/벡터 생성
        collectAndSaveDocuments(request);

        // [4단계] 벡터(하이브리드) → 후보 수집 → BM25 → RRF (연도 필터 적용)
        List<VectorResult> vectorResults =
                vectorScorer.scoreHybrid(claimId, queryVector, CORPUS_TOP_K, CORPUS_FACT_LIMIT,
                        request.fromYear(), request.toYear());

        Map<UUID, EvidenceDocument> candidateMap = collectCandidateMap(claimId, vectorResults);

        List<RerankRow> rerankRows = rerank(request.canonicalClaim(), candidateMap, vectorResults);

        // [5단계] 재정렬 결과 저장 (연도 범위 포함 — 캐시 키)
        saveRerankResults(claimId, rerankRows, candidateMap, request.fromYear(), request.toYear());

        // [6단계] 해당 소주장을 claim_embedding에 저장 (다음 요청부터 캐시 대상)
        // 저장 실패가 결과 반환을 막지 않도록 방어
        saveClaimEmbedding(request, queryEmbedding);

        // [7단계] Top-K 근거문서 반환
        return getTopKDocuments(claimId);
    }

    /**
     * ===== 캐시 =====
     * 캐시 히트 여부 판정 → 재사용할 claimId (없으면 null)
     * 1) 유사 소주장 Top-K (유사도 내림차순)
     * 2) 유사도 ≥ 임계값 후보만
     * 3) 그 후보 중 연도범위 일치 + 유효기간 내인 claimId 집합 조회
     * 4) 유사도 순서대로 훑어 집합에 있는 첫 claimId 반환 (가장 유사한 유효 캐시)
     */
    private UUID findCacheHit(ClaimRetrievalRequest request, String queryVector) {
        // [1단계] 유사 소주장 후보
        List<SimilarClaimProjection> similar =
                claimEmbeddingRepository.findSimilarClaims(queryVector, SIMILAR_CLAIM_LIMIT);
        if (similar.isEmpty()) {
            return null;
        }

        // [2단계] 임계값 이상만, 유사도 내림차순 유지 (쿼리가 이미 정렬)
        List<UUID> candidateIds = similar.stream()
                .filter(s -> s.getSimilarity() != null && s.getSimilarity() >= SIMILARITY_THRESHOLD)
                .map(SimilarClaimProjection::getClaimId)
                .toList();
        if (candidateIds.isEmpty()) {
            return null;
        }

        // [3단계] 연도범위 일치 + 유효기간 내인 claimId 집합
        LocalDateTime cutoff = LocalDateTime.now().minusDays(CACHE_VALID_DAYS);
        Set<UUID> cacheable = Set.copyOf(rerankResultRepository.findCacheableClaimIds(
                candidateIds, request.fromYear(), request.toYear(), cutoff));
        if (cacheable.isEmpty()) {
            return null;
        }

        // [4단계] 유사도 순서대로 훑어 통과 집합에 있는 첫 claimId (가장 유사한 유효 캐시)
        for (UUID id : candidateIds) {
            if (cacheable.contains(id)) {
                return id;
            }
        }
        return null;
    }

    /**
     * 소주장 임베딩 저장 (캐시 미스로 새로 검색한 경우에만)
     * category(enum) → Category 엔티티 매핑 필요, 저장 실패는 로그만 남기고 무시(결과 반환 우선)
     */

    // [6단계] 해당 소주장을 claim_embedding에 저장 (다음 요청부터 캐시 대상)
    private void saveClaimEmbedding(ClaimRetrievalRequest request, float[] queryEmbedding) {
        try {
            // 이미 저장돼 있으면 스킵 (동일 claimId 재요청 등)
            if (claimEmbeddingRepository.findByClaimId(request.claimId()).isPresent()) {
                return;
            }

            Category category = categoryRepository.findByCategoryName(request.category())
                    .orElse(null);
            if (category == null) {
                log.warn("[캐시 저장] category 매핑 없음 → claim_embedding 저장 스킵. category={}, claimId={}",
                        request.category(), request.claimId());
                return;
            }

            ClaimEmbedding embedding = ClaimEmbedding.builder()
                    .category(category)
                    .claimId(request.claimId())
                    .claimText(request.canonicalClaim())
                    .claimVector(queryEmbedding)
                    .build();
            claimEmbeddingRepository.save(embedding);
            log.info("[캐시 저장] claim_embedding 저장 완료. claimId={}", request.claimId());
        } catch (Exception e) {
            // 캐시 저장 실패가 검색 결과 반환을 막지 않도록
            log.error("[캐시 저장] claim_embedding 저장 실패(무시). claimId={}: {}",
                    request.claimId(), e.getMessage(), e);
        }
    }

    // ===== 검색 단계별 세부 로직 =====
    // [3단계] 근거문서 수집·저장
    private void collectAndSaveDocuments(ClaimRetrievalRequest request) {
        documentCollector.collectAndSave(request);
    }

    /**
     * [4-1단계] 후보 문서 맵 수집 (per-claim + 코퍼스)
     */
    private Map<UUID, EvidenceDocument> collectCandidateMap(UUID claimId, List<VectorResult> vectorResults) {
        Map<UUID, EvidenceDocument> candidateMap = new LinkedHashMap<>();

        List<EvidenceDocument> perClaim = evidenceDocumentRepository.findAllByClaimId(claimId);
        for (EvidenceDocument d : perClaim) {
            candidateMap.put(d.getEvidenceDocumentId(), d);
        }

        List<UUID> corpusDocIds = vectorResults.stream()
                .map(VectorResult::evidenceDocumentId)
                .filter(id -> !candidateMap.containsKey(id))
                .toList();
        if (!corpusDocIds.isEmpty()) {
            for (EvidenceDocument d : evidenceDocumentRepository.findAllById(corpusDocIds)) {
                candidateMap.put(d.getEvidenceDocumentId(), d);
            }
        }

        log.info("후보 수집 완료. claimId={}, per-claim={}, 코퍼스={}, 총 후보={}",
                claimId, perClaim.size(), corpusDocIds.size(), candidateMap.size());
        return candidateMap;
    }

    // [4-2~4단계] BM25 + RRF
    private List<RerankRow> rerank(String claimText,
                                   Map<UUID, EvidenceDocument> candidateMap,
                                   List<VectorResult> vectorResults) {
        if (candidateMap.isEmpty()) {
            log.info("후보 근거문서 없음 → 재정렬 스킵.");
            return Collections.emptyList();
        }

        List<EvidenceDocument> candidates = new ArrayList<>(candidateMap.values());
        List<Bm25Result> bm25Results = bm25Scorer.score(claimText, candidates);
        return rrfFusion.fuse(bm25Results, vectorResults);
    }

    // [5단계] 재정렬 결과 저장 (기존 내역 덮어쓰기) + 연도 범위(캐시 키) 저장
    private void saveRerankResults(UUID claimId, List<RerankRow> rows,
                                   Map<UUID, EvidenceDocument> candidateMap,
                                   Integer fromYear, Integer toYear) {
        rerankResultRepository.deleteByClaimId(claimId);

        if (rows.isEmpty()) {
            return;
        }

        List<RerankResult> entities = new ArrayList<>(rows.size());
        for (RerankRow row : rows) {
            EvidenceDocument doc = candidateMap.get(row.evidenceDocumentId());
            if (doc == null) continue;

            entities.add(RerankResult.builder()
                    .claimId(claimId)
                    .evidenceDocument(doc)
                    .bm25Rank(row.bm25Rank())
                    .bm25Score(row.bm25Score())
                    .vectorSimRank(row.vectorRank())
                    .vectorSimScore(row.vectorScore())
                    .finalScore(row.finalScore())
                    .finalRank(row.finalRank())
                    .fromYear(fromYear)   // 캐시 키 — 해당 결과가 검색된 연도 범위
                    .toYear(toYear)
                    .build());
        }
        rerankResultRepository.saveAll(entities);
    }

    // [7단계] Top-K 근거문서 반환
    private List<RetrievedEvidence> getTopKDocuments(UUID claimId) {
        List<RerankResult> ranked = rerankResultRepository.findAllByClaimIdOrderByFinalRankAsc(claimId);
        if (ranked.isEmpty()) {
            return Collections.emptyList();
        }
        return ranked.stream()
                .limit(TOP_K)
                .map(this::toRetrievedEvidence)
                .collect(Collectors.toList());
    }

    // 최종 반환 DTO 변환
    private RetrievedEvidence toRetrievedEvidence(RerankResult r) {
        EvidenceDocument doc = r.getEvidenceDocument();
        return new RetrievedEvidence(
                doc.getEvidenceDocumentId(),
                doc.getTitle(),
                doc.getContentCleaned(),
                doc.getApiName(),
                doc.getSearchKeyword(),
                doc.getCategoryName(),
                doc.getPublishedAt(),
                doc.getOriginalUrl(),
                doc.getAuthorOrDept(),
                r.getBm25Score(),
                r.getBm25Rank(),
                r.getVectorSimScore(),
                r.getVectorSimRank(),
                r.getFinalScore(),
                r.getFinalRank()
        );
    }
}
