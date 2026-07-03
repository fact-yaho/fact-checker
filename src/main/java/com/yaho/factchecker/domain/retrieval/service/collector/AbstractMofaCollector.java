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
 * 국가별 dedup 후 EvidenceDocument 목록 반환
 * 구현체는 fetchByCountry(국가명) 와 country별 item→EvidenceDocument 매핑만 정의
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

    @Override
    public List<EvidenceDocument> collect(ClaimRetrievalRequest request) {
        UUID claimId = request.claimId();
        ClaimCategory category = request.category();
        List<ClaimRetrievalRequest.CountryInfo> countries = request.countries();

        if (countries == null || countries.isEmpty()) {
            log.info("[{}] 대상 국가 없음 → 수집 스킵. claimId={}", apiName(), claimId);
            return List.of();
        }

        // 국가별 조회 → dedup
        Map<String, EvidenceDocument> deduped = new LinkedHashMap<>();
        for (ClaimRetrievalRequest.CountryInfo country : countries) {
            String countryName = country.name();
            if (countryName == null || countryName.isBlank()) {
                continue;
            }

            List<T> items = fetchByCountry(countryName);
            for (T item : items) {
                String key = dedupKey(item);
                if (key == null || key.isBlank()) {
                    // 최후 폴백(중복 방지용 유니크)
                    key = countryName + "-" + deduped.size();
                }
                deduped.putIfAbsent(key, toEvidenceDocument(claimId, category, countryName, item));
            }
        }

        List<EvidenceDocument> result = new ArrayList<>(deduped.values());
        log.info("[{}] {}건 수집. claimId={}", apiName(), result.size(), claimId);
        return result;
    }
}
