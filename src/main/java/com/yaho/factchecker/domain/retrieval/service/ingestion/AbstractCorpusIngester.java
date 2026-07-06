package com.yaho.factchecker.domain.retrieval.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.domain.retrieval.service.DocumentFactWriter;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.global.type.IngestionFailureStage;
import com.yaho.factchecker.global.type.SourceType;
import com.yaho.factchecker.infrastructure.retrieval.ContentCleaner;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 코퍼스 적재 공통 베이스
 * [흐름] 페이지 순회 → 문서별로: 정제 → (fact 추출·임베딩·저장은 DocumentFactWriter 위임)
 * 한 문서 처리 실패는 로그 + ingestion_failure 기록 후 다음 문서 계속(resilient)
 *
 * 문서 저장/fact 저장의 트랜잭션 경계는 DocumentFactWriter → DocumentPersister가 담당
 * 실패 기록 저장은 IngestionFailureRecorder(별도 짧은 트랜잭션)가 담당
 *
 * T = 해당 API의 item 타입
 */
@Slf4j
public abstract class AbstractCorpusIngester<T> implements CorpusIngester {

    protected final ContentCleaner contentCleaner;
    protected final DocumentFactWriter documentFactWriter;
    protected final IngestionFailureRecorder failureRecorder;
    protected final ObjectMapper objectMapper;

    protected AbstractCorpusIngester(ContentCleaner contentCleaner,
                                     DocumentFactWriter documentFactWriter,
                                     IngestionFailureRecorder failureRecorder,
                                     ObjectMapper objectMapper) {
        this.contentCleaner = contentCleaner;
        this.documentFactWriter = documentFactWriter;
        this.failureRecorder = failureRecorder;
        this.objectMapper = objectMapper;
    }

    // ===== 구현체가 API별로 채우는 부분 =====
    // 한 페이지 조회 → item 목록 (실패 시 예외를 던짐 — 베이스가 FETCH 실패로 기록)
    protected abstract List<T> fetchPage(int pageNo, int numOfRows);

    // 재처리용 — 이 적재기의 item 타입 (JSON 역직렬화에 사용)
    protected abstract Class<T> itemType();

    // 해당 API 문서의 카테고리 (대체로 공식입장/외교정책 계열)
    protected abstract ClaimCategory category();

    protected abstract String getTitle(T item);
    protected abstract String getRawContent(T item);     // HTML 원문
    protected abstract String getPublishedAtRaw(T item); // "yyyy-MM-dd..." 문자열
    protected abstract String getOriginalUrl(T item);
    protected abstract String getAuthorOrDept(T item);

    // ===== 공통 적재 로직 =====
    @Override
    public IngestionResult ingest(int numOfRows, int maxPages) {
        int processed = 0, success = 0, failure = 0, savedFacts = 0;

        for (int page = 1; page <= maxPages; page++) {
            // 페이지 조회 (실패 시 FETCH 단계로 기록, 다음 페이지로)
            List<T> items;
            try {
                items = fetchPage(page, numOfRows);
            } catch (Exception e) {
                log.error("[{}] page {} 조회 실패: {}", apiName(), page, e.getMessage(), e);
                // 페이지 단위 실패 — item 이 없으므로 null 로 기록 (page 정보는 사유에)
                failureRecorder.record(apiName(), null, IngestionFailureStage.FETCH,
                        "page " + page + " 조회 실패: " + e.getMessage());
                continue;
            }

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
                } catch (IngestionStageException e) {
                    // 단계 정보가 있는 실패 — 해당 단계로 기록
                    failure++;
                    log.error("[{}] 문서 적재 실패 [{}] (title='{}'): {}",
                            apiName(), e.stage(), safeTitle(item), e.getMessage());
                    failureRecorder.record(apiName(), item, e.stage(), e.getMessage());
                } catch (Exception e) {
                    // 단계 미상 실패 — WRITE 로 간주 (대부분 writeFacts 내부)
                    failure++;
                    log.error("[{}] 문서 적재 실패 (title='{}'): {}",
                            apiName(), safeTitle(item), e.getMessage(), e);
                    failureRecorder.record(apiName(), item, IngestionFailureStage.WRITE, e.getMessage());
                }
            }
        }

        IngestionResult result = new IngestionResult(apiName(), processed, success, failure, savedFacts);
        log.info("[{}] 적재 완료 - 처리 {}, 성공 {}, 실패 {}, fact {}개",
                apiName(), processed, success, failure, savedFacts);
        return result;
    }

    /**
     * 문서 1건 적재
     * 정제 → 미저장 EvidenceDocument 생성 → DocumentFactWriter 위임(fact 추출·임베딩·저장)
     * 단계별 실패는 IngestionStageException으로 감싸 던져, 상위에서 단계 기록
     * 반환 = 저장된 fact 수
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

        // [2단계] 미저장 문서 생성 (CORPUS), 저장은 writeFacts 내부에서 수행
        EvidenceDocument doc = EvidenceDocument.builder()
                .sourceType(SourceType.CORPUS) // claim_id 없음
                .apiName(apiName())
                .title(getTitle(item))
                .contentCleaned(cleaned)
                .categoryName(category())
                .publishedAt(parseDate(getPublishedAtRaw(item)))
                .originalUrl(getOriginalUrl(item))
                .authorOrDept(getAuthorOrDept(item))
                .build();

        // [3단계] fact 추출 + 임베딩 + 저장 (실패 시 WRITE)
        try {
            return documentFactWriter.writeFacts(doc).savedFactCount();
        } catch (Exception e) {
            throw new IngestionStageException(IngestionFailureStage.WRITE, e.getMessage(), e);
        }
    }

    /**
     * 재처리 — 원본 item JSON을 이 적재기의 타입으로 복원 후 ingestOne 재실행
     * 성공 시 저장된 fact 수 반환, 실패 시 예외 전파(호출측이 재시도 횟수/사유 갱신)
     */
    @Override
    public int retryOne(String itemJson) {
        T item;
        try {
            item = objectMapper.readValue(itemJson, itemType());
        } catch (Exception e) {
            // 복원 실패 — 재처리 불가 (원본 JSON 손상 등)
            throw new IllegalStateException("item JSON 복원 실패: " + e.getMessage(), e);
        }
        return ingestOne(item);  // 재처리 성공/실패는 그대로 전파
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

    // 단계 정보를 담은 내부 예외 (실패 단계 기록용)
    protected static class IngestionStageException extends RuntimeException {
        private final IngestionFailureStage stage;
        public IngestionStageException(IngestionFailureStage stage, String message, Throwable cause) {
            super(message, cause);
            this.stage = stage;
        }
        public IngestionFailureStage stage() { return stage; }
    }
}
