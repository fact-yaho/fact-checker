package com.yaho.factchecker.domain.retrieval.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.domain.retrieval.repository.EvidenceDocumentRepository;
import com.yaho.factchecker.domain.retrieval.service.DocumentFactWriter;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.global.type.IngestionFailureStage;
import com.yaho.factchecker.global.type.SourceType;
import com.yaho.factchecker.global.util.HashUtils;
import com.yaho.factchecker.infrastructure.retrieval.ContentCleaner;
import com.yaho.factchecker.infrastructure.retrieval.dto.MofaResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 코퍼스 적재 공통 베이스
 * [흐름] 페이지 순회 → 문서별로: 정제 → 중복 체크 → (fact 추출·임베딩·저장은 DocumentFactWriter 위임)
 * 한 문서 처리 실패는 로그 + ingestion_failure 기록 후 다음 문서 계속(resilient)
 *
 * ingest = 앞 페이지부터 (대량 초기 적재)
 * ingestRecent = 마지막 페이지부터 recentPages 페이지 (스케줄러 증분 — 대상 API 가 오래된 순 정렬)
 *
 * 문서 저장/fact 저장 트랜잭션은 DocumentFactWriter → DocumentPersister
 * 실패 기록은 IngestionFailureRecorder(별도 짧은 트랜잭션)
 * 코퍼스 중복은 content 해시로 방지 (LLM 호출 전 체크)
 *
 * T = 해당 API의 item 타입
 */
@Slf4j
public abstract class AbstractCorpusIngester<T> implements CorpusIngester {

    protected final ContentCleaner contentCleaner;
    protected final DocumentFactWriter documentFactWriter;
    protected final IngestionFailureRecorder failureRecorder;
    protected final ObjectMapper objectMapper;
    protected final EvidenceDocumentRepository evidenceDocumentRepository; // 중복 체크용

    protected AbstractCorpusIngester(ContentCleaner contentCleaner,
                                     DocumentFactWriter documentFactWriter,
                                     IngestionFailureRecorder failureRecorder,
                                     ObjectMapper objectMapper,
                                     EvidenceDocumentRepository evidenceDocumentRepository) {
        this.contentCleaner = contentCleaner;
        this.documentFactWriter = documentFactWriter;
        this.failureRecorder = failureRecorder;
        this.objectMapper = objectMapper;
        this.evidenceDocumentRepository = evidenceDocumentRepository;
    }

    // ===== 구현체가 API별로 채우는 부분 =====
    // 한 페이지 조회 → 응답 전체 (items + totalCount). 실패 시 예외를 던짐
    protected abstract MofaResponse<T> fetchPage(int pageNo, int numOfRows);

    // 재처리용 — 해당 적재기의 item 타입 (JSON 역직렬화에 사용)
    protected abstract Class<T> itemType();

    // 해당 API 문서의 카테고리
    protected abstract ClaimCategory category();

    protected abstract String getTitle(T item);
    protected abstract String getRawContent(T item);
    protected abstract String getPublishedAtRaw(T item);
    protected abstract String getOriginalUrl(T item);
    protected abstract String getAuthorOrDept(T item);

    // ===== 앞 페이지부터 적재 (대량 초기 적재) =====
    @Override
    public IngestionResult ingest(int numOfRows, int maxPages) {
        Counters c = new Counters();

        for (int page = 1; page <= maxPages; page++) {
            List<T> items = fetchPageItems(page, numOfRows, c);
            if (items == null) continue; // FETCH 실패 (이미 기록됨)
            if (items.isEmpty()) {
                log.info("[{}] page {} 결과 없음 → 조회 종료", apiName(), page);
                break;
            }
            log.info("[{}] page {} - {}건 조회", apiName(), page, items.size());
            processItems(items, page, c);
        }

        return summarize(c);
    }

    // ===== 마지막 페이지부터 적재 (스케줄러 증분) =====
    @Override
    public IngestionResult ingestRecent(int numOfRows, int recentPages) {
        Counters c = new Counters();

        // [1단계] totalCount 확인 (page 1 호출 — totalCount 만 사용, 문서 처리 안 함)
        int totalCount;
        try {
            MofaResponse<T> first = fetchPage(1, numOfRows);
            if (!first.isSuccess()) {
                log.warn("[{}] 최신 적재 - totalCount 조회 실패(응답 실패)", apiName());
                return summarize(c);
            }
            totalCount = first.totalCount();
        } catch (Exception e) {
            log.error("[{}] 최신 적재 - totalCount 조회 실패: {}", apiName(), e.getMessage(), e);
            failureRecorder.record(apiName(), null, IngestionFailureStage.FETCH,
                    "최신 적재 totalCount 조회 실패: " + e.getMessage());
            return summarize(c);
        }

        if (totalCount <= 0) {
            log.info("[{}] 최신 적재 - 데이터 없음(totalCount=0)", apiName());
            return summarize(c);
        }

        // [2단계] 마지막 페이지 계산 → 뒤 recentPages 페이지 범위
        int lastPage = (totalCount + numOfRows - 1) / numOfRows;   // ceil
        int startPage = Math.max(1, lastPage - recentPages + 1);
        log.info("[{}] 최신 적재 - totalCount={}, lastPage={}, 조회범위 {}~{}",
                apiName(), totalCount, lastPage, startPage, lastPage);

        // [3단계] startPage..lastPage 처리 (오래된 순이라 이 구간이 최신)
        for (int page = startPage; page <= lastPage; page++) {
            List<T> items = fetchPageItems(page, numOfRows, c);
            if (items == null || items.isEmpty()) continue;
            log.info("[{}] page {} - {}건 조회", apiName(), page, items.size());
            processItems(items, page, c);
        }

        return summarize(c);
    }

    // ===== 공통 헬퍼 =====
    // 페이지 조회 → items, 실패 시 FETCH 기록하고 null 반환
    private List<T> fetchPageItems(int page, int numOfRows, Counters c) {
        try {
            MofaResponse<T> response = fetchPage(page, numOfRows);
            if (!response.isSuccess()) {
                log.warn("[{}] API 실패 응답 (page={})", apiName(), page);
                return List.of();
            }
            return response.items();
        } catch (Exception e) {
            log.error("[{}] page {} 조회 실패: {}", apiName(), page, e.getMessage(), e);
            failureRecorder.record(apiName(), null, IngestionFailureStage.FETCH,
                    "page " + page + " 조회 실패: " + e.getMessage());
            return null;
        }
    }

    // item 목록 처리 (문서별 적재 + 실패 기록)
    private void processItems(List<T> items, int page, Counters c) {
        for (T item : items) {
            c.processed++;
            try {
                int facts = ingestOne(item);
                c.savedFacts += facts;
                c.success++;
            } catch (IngestionStageException e) {
                c.failure++;
                log.error("[{}] 문서 적재 실패 [{}] (title='{}'): {}",
                        apiName(), e.stage(), safeTitle(item), e.getMessage());
                failureRecorder.record(apiName(), item, e.stage(), e.getMessage());
            } catch (Exception e) {
                c.failure++;
                log.error("[{}] 문서 적재 실패 (title='{}'): {}",
                        apiName(), safeTitle(item), e.getMessage(), e);
                failureRecorder.record(apiName(), item, IngestionFailureStage.WRITE, e.getMessage());
            }
        }
    }

    private IngestionResult summarize(Counters c) {
        IngestionResult result = new IngestionResult(apiName(), c.processed, c.success, c.failure, c.savedFacts);
        log.info("[{}] 적재 완료 - 처리 {}, 성공 {}, 실패 {}, fact {}개",
                apiName(), c.processed, c.success, c.failure, c.savedFacts);
        return result;
    }

    /**
     * 문서 1건 적재
     * 정제 → 중복 체크(코퍼스) → 미저장 EvidenceDocument 생성 → DocumentFactWriter 위임
     * 단계별 실패는 IngestionStageException으로 감싸 던짐
     * 반환 = 저장된 fact 수 (중복/빈 문서는 0)
     */
    protected int ingestOne(T item) {
        // [1단계] 정제 (실패 시 CLEAN)
        String cleaned;
        try {
            cleaned = contentCleaner.clean(getRawContent(item));
        } catch (Exception e) {
            throw new IngestionStageException(IngestionFailureStage.CLEAN, e.getMessage(), e);
        }
        if (cleaned.isBlank()) {
            log.warn("[{}] 정제 결과 빈 문서 → 스킵 (title='{}')", apiName(), safeTitle(item));
            return 0;
        }

        // [2단계] 중복 체크 (LLM 전) — 같은 코퍼스 문서가 이미 있으면 스킵
        String contentHash = HashUtils.sha256(cleaned);
        if (evidenceDocumentRepository.existsBySourceTypeAndContentHash(SourceType.CORPUS, contentHash)) {
            log.info("[{}] 중복 문서 → 스킵 (title='{}')", apiName(), safeTitle(item));
            return 0;
        }

        // [3단계] 미저장 문서 생성 (CORPUS, content_hash 세팅)
        EvidenceDocument doc = EvidenceDocument.builder()
                .sourceType(SourceType.CORPUS)
                .apiName(apiName())
                .title(getTitle(item))
                .contentCleaned(cleaned)
                .categoryName(category())
                .publishedAt(parseDate(getPublishedAtRaw(item)))
                .originalUrl(getOriginalUrl(item))
                .authorOrDept(getAuthorOrDept(item))
                .contentHash(contentHash)
                .build();

        // [4단계] fact 추출 + 임베딩 + 저장 (실패 시 WRITE)
        try {
            return documentFactWriter.writeFacts(doc).savedFactCount();
        } catch (Exception e) {
            throw new IngestionStageException(IngestionFailureStage.WRITE, e.getMessage(), e);
        }
    }

    // "yyyy-MM-dd..." → LocalDateTime (앞 10자만), 파싱 실패/빈 값은 null
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

    // 재처리 — 원본 item JSON 을 이 적재기의 타입으로 복원 후 ingestOne 재실행
    @Override
    public int retryOne(String itemJson) {
        T item;
        try {
            item = objectMapper.readValue(itemJson, itemType());
        } catch (Exception e) {
            throw new IllegalStateException("item JSON 복원 실패: " + e.getMessage(), e);
        }
        return ingestOne(item);
    }

    // 집계 카운터
    private static class Counters {
        int processed = 0, success = 0, failure = 0, savedFacts = 0;
    }

    // 단계 정보를 담은 내부 예외
    protected static class IngestionStageException extends RuntimeException {
        private final IngestionFailureStage stage;
        public IngestionStageException(IngestionFailureStage stage, String message, Throwable cause) {
            super(message, cause);
            this.stage = stage;
        }
        public IngestionFailureStage stage() { return stage; }
    }
}
