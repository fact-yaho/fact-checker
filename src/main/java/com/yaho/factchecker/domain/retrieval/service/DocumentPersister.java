package com.yaho.factchecker.domain.retrieval.service;

import com.yaho.factchecker.domain.retrieval.entity.DocumentFact;
import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.domain.retrieval.repository.DocumentFactRepository;
import com.yaho.factchecker.domain.retrieval.repository.EvidenceDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 문서 + document_fact 저장만 담당하는 빈 (짧은 트랜잭션 경계)
 *
 * LLM fact 추출·임베딩(느린 외부 호출)은 이 트랜잭션 밖에서 수행하고,
 * 실제 DB 저장(문서 + fact)만 이 빈의 @Transactional 메서드로 묶어 커넥션 점유 시간을 최소화
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentPersister {

    private final EvidenceDocumentRepository evidenceDocumentRepository;
    private final DocumentFactRepository documentFactRepository;

    /**
     * 문서 + fact 를 한 트랜잭션으로 저장
     * document = 저장할 EvidenceDocument (아직 미저장 상태)
     * facts = 정규화된 fact 텍스트 목록
     * vectors = facts 와 순서 1:1 대응하는 벡터 목록
     * 최종 반환 결과 = 저장된 EvidenceDocument (id 채워진 상태)
     */
    @Transactional
    public EvidenceDocument persist(EvidenceDocument document,
                                    List<String> facts, List<float[]> vectors) {
        // [1단계] 문서 저장
        EvidenceDocument savedDoc = evidenceDocumentRepository.save(document);

        // [2단계] fact 저장 (fact 텍스트 + 벡터, 순서 1:1 대응)
        if (facts != null && !facts.isEmpty()) {
            List<DocumentFact> factEntities = new ArrayList<>(facts.size());
            for (int i = 0; i < facts.size(); i++) {
                factEntities.add(DocumentFact.builder()
                        .evidenceDocument(savedDoc)
                        .factText(facts.get(i))
                        .factVector(vectors.get(i))
                        .build());
            }
            documentFactRepository.saveAll(factEntities);
        }

        return savedDoc;
    }

    // fact 없이 문서만 저장 (fact 0개인 문서용)
    @Transactional
    public EvidenceDocument persistDocumentOnly(EvidenceDocument document) {
        return evidenceDocumentRepository.save(document);
    }
}
