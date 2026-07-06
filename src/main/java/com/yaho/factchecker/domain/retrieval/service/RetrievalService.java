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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
* 근거 탐색 오케스트레이터
*
* [제공 기능]
* 1. 소주장 하나에 대한 캐싱 조회
* 2. 소주장 하나에 대한 상위 K개의 근거 문서를 제공
* */

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

    // CACHE_TTL = 특정 소주장에 대해 캐싱 유효 기간
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    /*
     * [retrieveEvidence() 메서드 설명]
     *
     * 소주장 1개에 대한 상위 근거문서 K개를 얻도록 하는 공개 진입점, 오케스트레이터 역할
     *
     * claim-analysis에 의해 추출된 정제된 소주장 별로 ClaimRetrievalRequest DTO 형태로
     * 넘기면 해당 소주장에 대한 상위 K개의 문서를 List<RetrievedEvidence> 형태로 제공
     *
     * request = Claim-analysis 및 LLM 분석 결과로 얻은 소주장 1개에 대한 정보
     *
     * 최종 반환 값 = 상위 K개의 근거문서 + 점수/순위 (순위 오름차순), 검증 불가/결과 없으면 빈 리스트를 반환
     * */

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

        // [3단계] 소주장에 대한 근거문서 수집·저장
        collectAndSaveDocuments(request);

        // [4단계] fact 추출·임베딩
        String queryVector = embedClaim(request.canonicalClaim());

        // [5단계] 벡터 + BM25 + RRF
        List<RerankRow> rerankRows = rerank(claimId, request.canonicalClaim(), queryVector);

        // [6단계] 재정렬 결과 저장 (기존 내역 있다면 덮어쓰기)
        saveRerankResults(claimId, rerankRows);

        // [7단계] Top-K 근거문서 반환
        return getTopKDocuments(claimId);
    }

    /*
    * 하단의 메서드들은 모두
    * "retrieveEvidence() - 근거 문서 오케스트레이터"에서 사용할 각 단계별 메서드임
    * */

    // [1단계] 유사질문(중복) 검사. TODO: 소주장 전달 흐름 완성 후 구현
    // private void similarClaimCheck(ClaimRetrievalRequest request) {
    //     // claimEmbeddingRepository.findSimilarClaims(queryVector, 5) + threshold 판단
    // }

    // [2단계] 캐시 유효성 판단. TODO: BaseEntity(created_at) 붙은 후 구현
    // private boolean isCacheValid(UUID claimId) {
    //     // 해당 소주장 rerank_result의 created_at 이 CACHE_TTL 이내인지
    //     return false;
    // }

    // [3단계] 근거문서 수집·저장
    private void collectAndSaveDocuments(ClaimRetrievalRequest request) {
        documentCollector.collectAndSave(request);
    }

    // [4단계] 소주장 텍스트를 임베딩하여 pgvector 리터럴 문자열로 반환
    private String embedClaim(String canonicalClaim) {
        float[] vector = textEmbedder.embed(canonicalClaim);
        return VectorUtils.toVectorLiteral(vector);
    }

    // [5단계] BM25 + 벡터 점수 + RRF
    private List<RerankRow> rerank(UUID claimId, String claimText, String queryVector) {
        // [5-1단계] 후보 근거문서 조회 (한 소주장이 수집한 문서들)
        List<EvidenceDocument> candidates = evidenceDocumentRepository.findAllByClaimId(claimId);

        // 근거문서가 없다면 빈 리스트 반환
        if (candidates.isEmpty()) {
            log.info("후보 근거문서 없음 → 재정렬 스킵. claimId={}", claimId);
            return Collections.emptyList();
        }

        // [5-2단계] 벡터 점수/순위
        List<VectorResult> vectorResults = vectorScorer.score(claimId, queryVector);

        // [5-3단계] BM25 점수/순위 (title + content_cleaned)
        List<Bm25Result> bm25Results = bm25Scorer.score(claimText, candidates);

        // [5-4단계] RRF 융합 → final_rank
        return rrfFusion.fuse(bm25Results, vectorResults);
    }

    // [6단계] 재정렬 결과 저장 (기존 내역 있다면 덮어쓰기)
    private void saveRerankResults(UUID claimId, List<RerankRow> rows) {
        if (rows.isEmpty()) {
            return;
        }

        // 해당 소주장의 기존 결과 삭제 (덮어쓰기)
        rerankResultRepository.deleteByClaimId(claimId);

        // RerankRow → RerankResult 엔티티 변환
        // evidence_document 연관관계를 위해 후보 문서를 id로 매핑
        List<EvidenceDocument> candidates = evidenceDocumentRepository.findAllByClaimId(claimId);
        Map<UUID, EvidenceDocument> docMap = candidates.stream()
                .collect(Collectors.toMap(EvidenceDocument::getEvidenceDocumentId, d -> d));

        List<RerankResult> entities = new ArrayList<>(rows.size());
        for (RerankRow row : rows) {
            EvidenceDocument doc = docMap.get(row.evidenceDocumentId());
            if (doc == null) continue; // 없는 경우에 대한 방어처리

            entities.add(RerankResult.builder()
                    .claimId(claimId)
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
        // final_rank 오름차순으로 rerank 결과 조회
        List<RerankResult> ranked = rerankResultRepository.findAllByClaimIdOrderByFinalRankAsc(claimId);
        if (ranked.isEmpty()) {
            return Collections.emptyList();
        }

        // 상위 K개의 문서를 순위 순서대로 추출
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
