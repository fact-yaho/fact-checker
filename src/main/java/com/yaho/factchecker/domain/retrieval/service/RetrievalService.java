package com.yaho.factchecker.domain.retrieval.service;

import com.yaho.factchecker.domain.retrieval.dto.Bm25Result;
import com.yaho.factchecker.domain.retrieval.dto.ClaimRetrievalRequest;
import com.yaho.factchecker.domain.retrieval.dto.RerankRow;
import com.yaho.factchecker.domain.retrieval.dto.RetrievedEvidence;
import com.yaho.factchecker.domain.retrieval.dto.VectorResult;
import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.domain.retrieval.entity.RerankResult;
import com.yaho.factchecker.domain.retrieval.repository.EvidenceDocumentRepository;
import com.yaho.factchecker.domain.retrieval.repository.RerankResultRepository;
import com.yaho.factchecker.global.util.VectorUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * 1. 소주장 하나에 대한 캐싱 조회
 * 2. 소주장 하나에 대한 상위 K개의 근거 문서를 제공 (claim_id로 좁힌 문서들 + 코퍼스)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final EvidenceDocumentRepository evidenceDocumentRepository;
    private final RerankResultRepository rerankResultRepository;
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

    /*
     * [retrieveEvidence() 메서드 설명]
     *
     * 소주장 1개에 대한 상위 근거문서 K개를 얻는 공개 진입점
     * per-claim(claim_id로 좁힌 문서) 수집 문서와 코퍼스 문서를 함께 후보로 하여 재정렬
     *
     * request = claim-analysis 결과로 얻은 소주장 1개 정보
     * 반환 = 상위 K개 근거문서 + 점수/순위 (순위 오름차순), 검증 불가/결과 없으면 빈 리스트
     */
    @Transactional
    public List<RetrievedEvidence> retrieveEvidence(ClaimRetrievalRequest request) {
        // [0단계] 검증 불가 소주장은 skip
        if (request == null || !request.isVerifiable()) {
            log.info("검증 불가 또는 빈 요청 → 근거검색 스킵. claimId={}",
                    request != null ? request.claimId() : null);
            return Collections.emptyList();
        }

        UUID claimId = request.claimId();

        // [1단계] 유사질문(중복) 검사 — TODO (소주장 전달 흐름 후)
        // similarClaimCheck(request);

        // [2단계] 캐시 확인 — TODO (BaseEntity created_at 기준 유효기간 판단)
        // if (isCacheValid(claimId)) {
        //     return getTopKDocuments(claimId);
        // }

        // [3단계] 소주장에 대한 근거문서 수집·저장 (per-claim), 수집 문서에 fact/벡터도 생성됨
        collectAndSaveDocuments(request);

        // [4단계] 소주장 임베딩
        String queryVector = embedClaim(request.canonicalClaim());

        // [5단계] 벡터(하이브리드) → 후보 수집(per-claim + 코퍼스) → BM25 → RRF
        // 벡터 점수는 여기서 한 번만 계산하고 후보 조회/RRF에 재사용
        List<VectorResult> vectorResults =
                vectorScorer.scoreHybrid(claimId, queryVector, CORPUS_TOP_K, CORPUS_FACT_LIMIT);

        Map<UUID, EvidenceDocument> candidateMap = collectCandidateMap(claimId, vectorResults);

        List<RerankRow> rerankRows = rerank(request.canonicalClaim(), candidateMap, vectorResults);

        // [6단계] 재정렬 결과 저장 (기존 내역 덮어쓰기), 후보 맵을 넘겨 코퍼스 문서 누락 방지
        saveRerankResults(claimId, rerankRows, candidateMap);

        // [7단계] Top-K 근거문서 반환
        return getTopKDocuments(claimId);
    }

    /*
     * 하단 메서드들은 retrieveEvidence()의 단계별 세부 로직
     */

    // [1단계] 유사질문(중복) 검사, TODO
    // private void similarClaimCheck(ClaimRetrievalRequest request) { ... }

    // [2단계] 캐시 유효성 판단, TODO
    // private boolean isCacheValid(UUID claimId) { return false; }

    // [3단계] 근거문서 수집·저장
    private void collectAndSaveDocuments(ClaimRetrievalRequest request) {
        documentCollector.collectAndSave(request);
    }

    // [4단계] 소주장 텍스트를 임베딩하여 pgvector 리터럴 문자열로 반환
    private String embedClaim(String canonicalClaim) {
        float[] vector = textEmbedder.embed(canonicalClaim);
        return VectorUtils.toVectorLiteral(vector);
    }

    /**
     * [5-1단계] 후보 문서 맵 수집 (per-claim + 코퍼스)
     * -> per-claim = 이 소주장이 수집한 문서 (claim_id로 조회)
     * -> 코퍼스 = 이미 계산된 하이브리드 벡터 결과에서 per-claim에 없는 문서 id를 추려 엔티티 조회
     * 벡터를 다시 계산하지 않고, 앞에서 구한 vectorResults를 재사용
     *
     * 최종 반환 결과 = evidence_document_id → 엔티티 맵 (BM25/저장 공용)
     */
    private Map<UUID, EvidenceDocument> collectCandidateMap(UUID claimId, List<VectorResult> vectorResults) {
        Map<UUID, EvidenceDocument> candidateMap = new LinkedHashMap<>();

        // per-claim 후보
        List<EvidenceDocument> perClaim = evidenceDocumentRepository.findAllByClaimId(claimId);
        for (EvidenceDocument d : perClaim) {
            candidateMap.put(d.getEvidenceDocumentId(), d);
        }

        // 벡터 결과 중 per-claim 에 없는 문서(= 코퍼스 문서) id만 추려 조회
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

    /**
     * [5-2~4단계] BM25 + RRF
     * 후보 맵(perClaim+corpus) 전체에 BM25를 매기고, 앞에서 구한 벡터 결과와 RRF 융합
     */
    private List<RerankRow> rerank(String claimText,
                                   Map<UUID, EvidenceDocument> candidateMap,
                                   List<VectorResult> vectorResults) {
        if (candidateMap.isEmpty()) {
            log.info("후보 근거문서 없음 → 재정렬 스킵.");
            return Collections.emptyList();
        }

        List<EvidenceDocument> candidates = new ArrayList<>(candidateMap.values());

        // BM25 = 합친 후보 전체 대상
        List<Bm25Result> bm25Results = bm25Scorer.score(claimText, candidates);

        // RRF 융합 (BM25 + 하이브리드 벡터)
        return rrfFusion.fuse(bm25Results, vectorResults);
    }

    /**
     * [6단계] 재정렬 결과 저장 (기존 내역 덮어쓰기)
     * 후보 맵(perClaim+corpus)으로 docMap을 만들어 코퍼스 문서도 저장
     * 기존 결과 삭제를 빈 리스트 검사보다 먼저 수행 (이번 결과가 0건이어도 낡은 결과가 남지 않도록)
     */
    private void saveRerankResults(UUID claimId, List<RerankRow> rows,
                                   Map<UUID, EvidenceDocument> candidateMap) {
        // 해당 소주장의 기존 결과 삭제 (덮어쓰기)
        rerankResultRepository.deleteByClaimId(claimId);

        if (rows.isEmpty()) {
            return;
        }

        List<RerankResult> entities = new ArrayList<>(rows.size());
        for (RerankRow row : rows) {
            EvidenceDocument doc = candidateMap.get(row.evidenceDocumentId());
            if (doc == null) continue; // 방어 (후보에 없는 문서면 skip)

            entities.add(RerankResult.builder()
                    .claimId(claimId) // 코퍼스 문서도 이 소주장 결과로 저장
                    .evidenceDocument(doc)
                    .bm25Rank(row.bm25Rank())
                    .bm25Score(row.bm25Score())
                    .vectorSimRank(row.vectorRank())
                    .vectorSimScore(row.vectorScore())
                    .finalScore(row.finalScore())
                    .finalRank(row.finalRank())
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
