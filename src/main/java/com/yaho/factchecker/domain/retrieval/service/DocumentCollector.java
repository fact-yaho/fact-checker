package com.yaho.factchecker.domain.retrieval.service;

import com.yaho.factchecker.domain.retrieval.dto.ClaimRetrievalRequest;
import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.domain.retrieval.service.collector.ApiDocumentCollector;
import com.yaho.factchecker.global.type.ClaimCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 근거문서 수집 오케스트레이터 (라우터)
 * 소주장 카테고리에 해당하는 수집기(ApiDocumentCollector)들만 골라 실행하고,
 * 각 문서에서 fact 를 추출·임베딩하여 저장 (문서 + document_fact 저장)
 *
 * per-claim 문서도 fact 벡터를 갖게 되어, 재정렬 시 BM25 + 벡터 양쪽 신호를 받음
 * (코퍼스 문서와 동일하게 DocumentFactWriter로 처리 — 문서 저장/fact 저장 트랜잭션은
 *  DocumentFactWriter → DocumentPersister가 담당)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentCollector {

    private final List<ApiDocumentCollector> collectors;
    private final DocumentFactWriter documentFactWriter;

    public void collectAndSave(ClaimRetrievalRequest request) {
        UUID claimId = request.claimId();
        ClaimCategory category = request.category();

        if (category == null) {
            log.info("카테고리 없음 → 수집 스킵. claimId={}", claimId);
            return;
        }

        // 이 카테고리를 담당하는 수집기만 선택
        List<ApiDocumentCollector> matched = collectors.stream()
                .filter(c -> c.supportedCategories().contains(category))
                .toList();

        if (matched.isEmpty()) {
            log.info("카테고리 [{}] 담당 수집기 없음 → 수집 스킵. claimId={}", category, claimId);
            return;
        }

        // 매칭된 수집기들 실행 → 결과 합침 (미저장 문서 목록)
        List<EvidenceDocument> collected = new ArrayList<>();
        for (ApiDocumentCollector collector : matched) {
            collected.addAll(collector.collect(request));
        }

        if (collected.isEmpty()) {
            log.info("수집 결과 없음. claimId={}, category={}", claimId, category);
            return;
        }

        // 각 문서에서 fact 추출·임베딩·저장 (문서 저장도 writeFacts 내부에서 수행)
        // 한 문서 실패는 로그 남기고 계속(resilient), 실패 문서는 벡터 없이 BM25 로만 참여(또는 미저장)
        int totalFacts = 0, failed = 0;
        for (EvidenceDocument doc : collected) {
            try {
                totalFacts += documentFactWriter.writeFacts(doc).savedFactCount();
            } catch (Exception e) {
                failed++;
                log.error("[per-claim fact] 문서 처리 실패 (title='{}'): {}",
                        doc.getTitle(), e.getMessage(), e);
            }
        }
        log.info("문서 저장 + fact 완료. claimId={}, category={}, 수집기={}개, fact {}개, 실패 {}건",
                claimId, category, matched.size(), totalFacts, failed);
    }
}
