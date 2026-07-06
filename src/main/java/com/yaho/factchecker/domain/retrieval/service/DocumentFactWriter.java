package com.yaho.factchecker.domain.retrieval.service;

import com.yaho.factchecker.application.ai.port.FactExtractionPort;
import com.yaho.factchecker.domain.ai.dto.request.FactExtractionRequest;
import com.yaho.factchecker.domain.ai.dto.response.FactExtractionResponse;
import com.yaho.factchecker.domain.retrieval.entity.DocumentFact;
import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.domain.retrieval.repository.DocumentFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 문서 하나에서 fact 를 추출·임베딩하여 document_fact 로 저장하는 공통 컴포넌트
 * 코퍼스 적재와 per-claim 수집이 공유함
 *
 * [흐름] content_cleaned → LLM fact 추출 → 배치 임베딩(fact별 개별 벡터) → document_fact 저장
 *
 * 저장할 fact가 없거나 content가 비면 아무것도 저장하지 않고 0 반환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentFactWriter {

    private final FactExtractionPort factExtractionPort;
    private final TextEmbedder textEmbedder;
    private final DocumentFactRepository documentFactRepository;

    /**
     * 이미 저장된 EvidenceDocument에 대해 fact 추출·임베딩·저장
     * document = content_cleaned 가 채워진, 저장된 EvidenceDocument
     * 최종 반환 결과 = 저장된 fact 수
     */
    public int writeFacts(EvidenceDocument document) {
        String content = document.getContentCleaned();
        if (content == null || content.isBlank()) {
            log.info("[factWriter] 본문 없음 → fact 추출 스킵 (title='{}')", safeTitle(document));
            return 0;
        }

        // [1단계] fact 추출 (LLM)
        FactExtractionResponse extracted =
                factExtractionPort.extract(new FactExtractionRequest(content));
        List<String> facts = (extracted.facts() != null) ? extracted.facts() : List.of();
        if (facts.isEmpty()) {
            log.info("[factWriter] 추출된 fact 없음 (title='{}')", safeTitle(document));
            return 0;
        }

        // [2단계] 배치 임베딩 (문서 단위, fact별 개별 벡터. 입력 순서 = 출력 순서)
        List<float[]> vectors = textEmbedder.embedBatch(facts);

        // [3단계] document_fact 저장 (fact 텍스트 + 벡터, 순서 1:1 대응)
        List<DocumentFact> factEntities = new ArrayList<>(facts.size());
        for (int i = 0; i < facts.size(); i++) {
            factEntities.add(DocumentFact.builder()
                    .evidenceDocument(document)
                    .factText(facts.get(i))
                    .factVector(vectors.get(i))
                    .build());
        }
        documentFactRepository.saveAll(factEntities);

        return factEntities.size();
    }

    private String safeTitle(EvidenceDocument doc) {
        return (doc.getTitle() != null) ? doc.getTitle() : "(unknown)";
    }
}
