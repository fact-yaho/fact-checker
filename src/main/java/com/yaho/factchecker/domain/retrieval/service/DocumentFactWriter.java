package com.yaho.factchecker.domain.retrieval.service;

import com.yaho.factchecker.application.ai.port.FactExtractionPort;
import com.yaho.factchecker.domain.ai.dto.request.FactExtractionRequest;
import com.yaho.factchecker.domain.ai.dto.response.FactExtractionResponse;
import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 문서 하나에서 fact를 추출·임베딩하여 저장하는 공통 컴포넌트
 * 코퍼스 적재와 per-claim 수집이 공유함
 *
 * [흐름] content_cleaned → LLM fact 추출 → 정규화(공백·null 제거) → 배치 임베딩 → 저장(위임)
 *
 * LLM 추출·임베딩은 트랜잭션 밖에서 수행하고, 실제 DB 저장은 DocumentPersister(@Transactional)에 위임
 * (느린 외부 호출을 트랜잭션 밖으로 빼 커넥션 점유 시간을 최소화)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentFactWriter {

    private final FactExtractionPort factExtractionPort;
    private final TextEmbedder textEmbedder;
    private final DocumentPersister documentPersister;

    /**
     * 아직 저장되지 않은 EvidenceDocument에 대해 fact 추출·임베딩 후 문서+fact를 함께 저장
     * document = content_cleaned가 채워진, 미저장 EvidenceDocument
     * 최종 반환 결과 = 저장된 문서와 저장된 fact 수
     */
    public WriteResult writeFacts(EvidenceDocument document) {
        String content = document.getContentCleaned();
        if (content == null || content.isBlank()) {
            log.info("[factWriter] 본문 없음 → fact 추출 스킵 (title='{}')", safeTitle(document));
            EvidenceDocument saved = documentPersister.persistDocumentOnly(document);
            return new WriteResult(saved, 0);
        }

        // [1단계] fact 추출 (LLM) — 트랜잭션 밖
        FactExtractionResponse extracted =
                factExtractionPort.extract(new FactExtractionRequest(content));

        // 정규화: null/공백 fact 제거 + trim
        // (LLM 응답에 빈 값이 섞이면 embedBatch가 예외를 던져 저장이 중단되므로 사전에 걸러냄)
        List<String> facts = (extracted.facts() != null ? extracted.facts() : List.<String>of()).stream()
                .filter(f -> f != null && !f.isBlank())
                .map(String::trim)
                .toList();
        if (facts.isEmpty()) {
            log.info("[factWriter] 추출된 fact 없음 (title='{}')", safeTitle(document));
            EvidenceDocument saved = documentPersister.persistDocumentOnly(document);
            return new WriteResult(saved, 0);
        }

        // [2단계] 배치 임베딩 (문서 단위, fact별 개별 벡터. 입력 순서 = 출력 순서) — 트랜잭션 밖
        List<float[]> vectors = textEmbedder.embedBatch(facts);

        // [3단계] 문서 + fact 저장 (DocumentPersister 의 짧은 트랜잭션에 위임)
        EvidenceDocument saved = documentPersister.persist(document, facts, vectors);

        return new WriteResult(saved, facts.size());
    }

    private String safeTitle(EvidenceDocument doc) {
        return (doc.getTitle() != null) ? doc.getTitle() : "(unknown)";
    }

    /**
     * 저장 결과
     * savedDocument = 저장된(id 채워진) 문서
     * savedFactCount = 저장된 fact 수
     */
    public record WriteResult(EvidenceDocument savedDocument, int savedFactCount) {}
}
