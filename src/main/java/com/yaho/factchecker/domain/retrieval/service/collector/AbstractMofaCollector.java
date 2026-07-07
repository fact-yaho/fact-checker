package com.yaho.factchecker.domain.retrieval.service.collector;

import com.yaho.factchecker.domain.retrieval.dto.ClaimRetrievalRequest;
import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.global.type.ClaimCategory;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * < MOFA country 기반 수집기 공통 베이스 >
 * 소주장 countries 순회 → 국가별 cond[country_nm::EQ] 조회
 * 국가별 dedup후 EvidenceDocument 목록 반환
 * 구현체는 fetchByCountry(국가명)와 country별 item→EvidenceDocument 매핑만 정의
 *
 * [연도 필터] request.fromYear()/toYear() 범위 밖 item은 fact 추출 전에 버림
 *   - 검색 미지원 API는 수집 후 fact 추출(LLM)을 태우므로, 범위 밖 문서를 수집 단계에서 거르면
 *     불필요한 LLM 비용·지연을 줄일 수 있음 (코퍼스는 쿼리 시점 필터라 위치가 다름)
 *   - item 의 연도는 extractYear(item)로 얻음, 기본은 null(연도 개념 없는 스냅샷 API)
 *     연도 있는 수집기만 extractYear를 오버라이드
 *   - extractYear가 null이면 필터하지 않고 포함(recall 우선), fromYear/toYear 둘 다
 *     null이면 필터 자체가 비활성
 *
 * <T> = 해당 API의 item 타입
 */
@Slf4j
public abstract class AbstractMofaCollector<T> implements ApiDocumentCollector {

    // API의 이름
    protected abstract String apiName();

    // 국가명으로 API 1회 호출 → item 목록, 실패 시 빈 리스트
    protected abstract List<T> fetchByCountry(String countryName);

    // item → EvidenceDocument (title/content 합성), claimId/apiName/searchKeyword/category는 베이스가 채움
    protected abstract EvidenceDocument toEvidenceDocument(
            UUID claimId, ClaimCategory category, String searchKeyword, T item);

    // item에서 dedup 키 추출 (보통 국가 ISO 코드), null이면 국가명으로 폴백
    protected abstract String dedupKey(T item);

    /**
     * item의 연도 추출 (연도 필터용)
     * 기본 구현은 null = 연도 개념 없는 API(스냅샷), 연도 있는 수집기만 오버라이드
     * 반환 null = 이 item 은 연도 필터에서 항상 포함(recall 우선)
     */
    protected Integer extractYear(T item) {
        return null;
    }

    @Override
    public List<EvidenceDocument> collect(ClaimRetrievalRequest request) {
        UUID claimId = request.claimId();
        ClaimCategory category = request.category();
        List<ClaimRetrievalRequest.CountryInfo> countries = request.countries();
        Integer fromYear = request.fromYear();
        Integer toYear = request.toYear();

        if (countries == null || countries.isEmpty()) {
            log.info("[{}] 대상 국가 없음 → 수집 스킵. claimId={}", apiName(), claimId);
            return List.of();
        }

        // 국가별 조회 → 연도 필터 → dedup
        int yearFiltered = 0;
        Map<String, EvidenceDocument> deduped = new LinkedHashMap<>();
        for (ClaimRetrievalRequest.CountryInfo country : countries) {
            String countryName = country.name();
            if (countryName == null || countryName.isBlank()) {
                continue;
            }

            List<T> items = fetchByCountry(countryName);
            for (T item : items) {
                // 연도 범위 밖이면 fact 추출 전에 버림 (extractYear가 null 이면 통과)
                if (!withinYearRange(extractYear(item), fromYear, toYear)) {
                    yearFiltered++;
                    continue;
                }

                String key = dedupKey(item);
                if (key == null || key.isBlank()) {
                    // 최후 폴백(중복 방지용 유니크)
                    key = countryName + "-" + deduped.size();
                }
                deduped.putIfAbsent(key, toEvidenceDocument(claimId, category, countryName, item));
            }
        }

        List<EvidenceDocument> result = new ArrayList<>(deduped.values());
        log.info("[{}] {}건 수집 (연도범위 밖 {}건 제외). claimId={}",
                apiName(), result.size(), yearFiltered, claimId);
        return result;
    }

    /**
     * 연도 범위 판정 (양끝 inclusive)
     * - year가 null → 항상 포함(연도 미상 문서는 버리지 않음, recall 우선)
     * - fromYear/toYear가 null → 해당 방향 무제한
     */
    private boolean withinYearRange(Integer year, Integer fromYear, Integer toYear) {
        if (year == null) return true;
        if (fromYear != null && year < fromYear) return false;
        if (toYear != null && year > toYear) return false;
        return true;
    }
}
