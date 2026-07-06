package com.yaho.factchecker.domain.retrieval.service.ingestion;

import com.yaho.factchecker.domain.retrieval.entity.DocumentFact;
import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.domain.retrieval.repository.DocumentFactRepository;
import com.yaho.factchecker.domain.retrieval.repository.EvidenceDocumentRepository;
import com.yaho.factchecker.domain.retrieval.service.TextEmbedder;
import com.yaho.factchecker.application.ai.port.FactExtractionPort;
import com.yaho.factchecker.domain.ai.dto.request.FactExtractionRequest;
import com.yaho.factchecker.domain.ai.dto.response.FactExtractionResponse;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.global.type.SourceType;
import com.yaho.factchecker.infrastructure.retrieval.ContentCleaner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * < 코퍼스 적재 공통 베이스 >
 * => 페이지 순회 → 문서별로: 정제 → 문서 저장 → fact 추출 → 배치 임베딩 → fact 저장
 *
 * 한 문서 처리 실패는 로그 남기고 다음 문서 계속(resilient), 실패 상세는 error 로그에 기록
 *
 * <T> = 해당 API의 item 타입
 */
@Slf4j
public abstract class AbstractCorpusIngester<T> implements CorpusIngester {

    protected final EvidenceDocumentRepository evidenceDocumentRepository;
    protected final DocumentFactRepository documentFactRepository;
    protected final ContentCleaner contentCleaner;
    protected final FactExtractionPort factExtractionPort;
    protected final TextEmbedder textEmbedder;

    protected AbstractCorpusIngester(EvidenceDocumentRepository evidenceDocumentRepository,
                                     DocumentFactRepository documentFactRepository,
                                     ContentCleaner contentCleaner,
                                     FactExtractionPort factExtractionPort,
                                     TextEmbedder textEmbedder) {
        this.evidenceDocumentRepository = evidenceDocumentRepository;
        this.documentFactRepository = documentFactRepository;
        this.contentCleaner = contentCleaner;
        this.factExtractionPort = factExtractionPort;
        this.textEmbedder = textEmbedder;
    }

    // 구현체가 API별로 채우는 부분
    // 한 페이지 조회 → item 목록 (실패 시 빈 리스트)
    protected abstract List<T> fetchPage(int pageNo, int numOfRows);

    // 해당 API 문서의 카테고리 (대체로 공식입장/외교정책 계열)
    protected abstract ClaimCategory category();

    protected abstract String getTitle(T item);
    protected abstract String getRawContent(T item);   // HTML 원문
    protected abstract String getPublishedAtRaw(T item); // "yyyy-MM-dd..." 문자열
    protected abstract String getOriginalUrl(T item);
    protected abstract String getAuthorOrDept(T item);

    // 공통 적재 로직
    @Override
    public IngestionResult ingest(int numOfRows, int maxPages) {
        int processed = 0, success = 0, failure = 0, savedFacts = 0;

        for (int page = 1; page <= maxPages; page++) {
            List<T> items = fetchPage(page, numOfRows);
            if (items.isEmpty()) {
                log.info("[{}] page {} 결과 없음 → 조회 종료", apiName(), page);
                break; // 더 이상 데이터 없으면 중단
            }
            log.info("[{}] page {} - {}건 조회", apiName(), page, items.size());

            for (T item : items) {
                processed++;
                try {
                    int facts = ingestOne(item);
                    savedFacts += facts;
                    success++;
                } catch (Exception e) {
                    failure++;
                    // 실패 문서 식별 정보를 명확히 남겨 나중에 확인/재시도 가능하게
                    log.error("[{}] 문서 적재 실패 (page={}, title='{}'): {}",
                            apiName(), page, safeTitle(item), e.getMessage(), e);
                }
            }
        }

        IngestionResult result = new IngestionResult(apiName(), processed, success, failure, savedFacts);
        log.info("[{}] 적재 완료 - 처리 {}, 성공 {}, 실패 {}, fact {}개",
                apiName(), processed, success, failure, savedFacts);
        return result;
    }

    /**
     * 문서 1건 적재,
     * 문서 저장 + fact 저장을 한 트랜잭션으로 묶음
     * 중간 실패 시 그 문서는 통째로 롤백(문서만 저장되고 fact 없는 상태 방지)
     *
     * 최종 반환 결과 = 저장된 fact 수
     */
    @Transactional
    protected int ingestOne(T item) {
        // [1단계] 정제
        String cleaned = contentCleaner.clean(getRawContent(item));
        if (cleaned.isBlank()) {
            log.warn("[{}] 정제 결과 빈 문서 → 스킵 (title='{}')", apiName(), safeTitle(item));
            return 0;
        }

        // [2단계] 문서 저장 (CORPUS)
        EvidenceDocument doc = EvidenceDocument.builder()
                .sourceType(SourceType.CORPUS)      // claim_id 없음
                .apiName(apiName())
                .title(getTitle(item))
                .contentCleaned(cleaned)
                .categoryName(category())
                .publishedAt(parseDate(getPublishedAtRaw(item)))
                .originalUrl(getOriginalUrl(item))
                .authorOrDept(getAuthorOrDept(item))
                .build();
        EvidenceDocument savedDoc = evidenceDocumentRepository.save(doc);

        // [3단계] fact 추출
        FactExtractionResponse extracted =
                factExtractionPort.extract(new FactExtractionRequest(cleaned));
        List<String> facts = (extracted.facts() != null) ? extracted.facts() : List.of();
        if (facts.isEmpty()) {
            log.info("[{}] 추출된 fact 없음 (title='{}')", apiName(), safeTitle(item));
            return 0;
        }

        // [4단계] 배치 임베딩 (문서 단위, fact별 개별 벡터)
        List<float[]> vectors = textEmbedder.embedBatch(facts);

        // [5단계] document_fact 저장 (fact 텍스트 + 벡터, 순서 1:1 대응)
        List<DocumentFact> factEntities = new ArrayList<>(facts.size());
        for (int i = 0; i < facts.size(); i++) {
            factEntities.add(DocumentFact.builder()
                    .evidenceDocument(savedDoc)
                    .factText(facts.get(i))
                    .factVector(vectors.get(i))
                    .build());
        }
        documentFactRepository.saveAll(factEntities);

        return factEntities.size();
    }

    // "yyyy-MM-dd..." → LocalDateTime (앞 10자만 사용), 파싱 실패/빈 값은 null
    protected LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String datePart = raw.trim();
            if (datePart.length() >= 10) datePart = datePart.substring(0, 10);
            return LocalDate.parse(datePart).atStartOfDay();
        } catch (Exception e) {
            log.warn("[{}] 날짜 파싱 실패: '{}'", apiName(), raw);
            return null;
        }
    }

    private String safeTitle(T item) {
        try { return getTitle(item); } catch (Exception e) { return "(unknown)"; }
    }
}
