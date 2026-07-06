package com.yaho.factchecker.domain.retrieval.service.collector;

import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.global.exception.BusinessException;
import com.yaho.factchecker.global.exception.ErrorCode;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.infrastructure.retrieval.dto.MofaResponse;
import com.yaho.factchecker.infrastructure.retrieval.dto.SituationItem;
import com.yaho.factchecker.infrastructure.retrieval.feign.MofaFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 대북관계 + 국제정세·다자외교 카테고리 — 국가·지역별 주요정세
@Slf4j
@Component
public class SituationCollector extends AbstractMofaCollector<SituationItem> {

    private final MofaFeignClient mofaFeignClient;
    private final String serviceKey;
    private final int numOfRows;

    public SituationCollector(MofaFeignClient mofaFeignClient,
                              @Value("${mofa.api.service-key}") String serviceKey,
                              @Value("${mofa.api.num-of-rows}") int numOfRows) {
        this.mofaFeignClient = mofaFeignClient;
        this.serviceKey = serviceKey;
        this.numOfRows = numOfRows;
    }

    @Override
    public Set<ClaimCategory> supportedCategories() {
        return Set.of(ClaimCategory.NORTH_KOREA_RELATIONS, ClaimCategory.INTERNATIONAL_AFFAIRS);
    }

    @Override
    protected String apiName() {
        return "외교부_국가·지역별 주요정세 정보";
    }

    @Override
    protected List<SituationItem> fetchByCountry(String countryName) {
        try {
            Map<String, String> conditions = new LinkedHashMap<>();
            conditions.put("cond[country_nm::EQ]", countryName);

            MofaResponse<SituationItem> response = mofaFeignClient.getSituations(
                    serviceKey, 1, numOfRows, "JSON", conditions);

            if (!response.isSuccess()) {
                throw new BusinessException(ErrorCode.PUBLIC_DATA_CALL_FAILED);
            }
            return response.items();
        } catch (BusinessException e) {
            log.warn("[{}] API 실패 응답. country='{}'", apiName(), countryName);
            return List.of();
        } catch (Exception e) {
            log.error("[{}] API 호출 실패. country='{}'", apiName(), countryName, e);
            return List.of();
        }
    }

    @Override
    protected String dedupKey(SituationItem item) {
        // 한 국가에 여러 정세 레코드 → 국가코드+연월일+내용으로 유니크
        return item.countryIsoAlp2() + "-" + item.year() + "-" + item.month() + "-" + item.day()
                + "-" + (item.situationInfoCn() != null ? item.situationInfoCn().hashCode() : 0);
    }

    @Override
    protected EvidenceDocument toEvidenceDocument(UUID claimId, ClaimCategory category,
                                                  String searchKeyword, SituationItem item) {
        String country = notBlank(item.countryNm()) ? item.countryNm() : item.countryEngNm();
        String date = formatDate(item.year(), item.month(), item.day());

        StringBuilder content = new StringBuilder();
        if (notBlank(date)) content.append("시점: ").append(date).append("\n");
        if (notBlank(item.situationInfoCn())) content.append("정세: ").append(item.situationInfoCn().trim());

        return EvidenceDocument.builder()
                .claimId(claimId)
                .apiName(apiName())
                .searchKeyword(searchKeyword)
                .title(country + " 주요정세" + (notBlank(date) ? " (" + date + ")" : ""))
                .contentCleaned(content.toString().trim())
                .categoryName(category)
                .build();
    }

    private String formatDate(Integer y, Integer m, Integer d) {
        if (y == null) return "";
        StringBuilder sb = new StringBuilder().append(y).append("년");
        if (m != null) sb.append(" ").append(m).append("월");
        if (d != null) sb.append(" ").append(d).append("일");
        return sb.toString();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
