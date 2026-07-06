package com.yaho.factchecker.domain.retrieval.service.collector;

import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.global.exception.BusinessException;
import com.yaho.factchecker.global.exception.ErrorCode;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.infrastructure.retrieval.dto.MofaResponse;
import com.yaho.factchecker.infrastructure.retrieval.dto.PeopleExchangeItem;
import com.yaho.factchecker.infrastructure.retrieval.feign.MofaFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 고위급 교류·정상외교 카테고리 — 국가(지역)별 주요인사 교류
@Slf4j
@Component
public class PeopleExchangeCollector extends AbstractMofaCollector<PeopleExchangeItem> {

    private final MofaFeignClient mofaFeignClient;
    private final String serviceKey;
    private final int numOfRows;

    public PeopleExchangeCollector(MofaFeignClient mofaFeignClient,
                                   @Value("${mofa.api.service-key}") String serviceKey,
                                   @Value("${mofa.api.num-of-rows}") int numOfRows) {
        this.mofaFeignClient = mofaFeignClient;
        this.serviceKey = serviceKey;
        this.numOfRows = numOfRows;
    }

    @Override
    public Set<ClaimCategory> supportedCategories() {
        return Set.of(ClaimCategory.HIGH_LEVEL_EXCHANGE);
    }

    @Override
    protected String apiName() {
        return "외교부_국가(지역)별 주요인사 교류";
    }

    @Override
    protected List<PeopleExchangeItem> fetchByCountry(String countryName) {
        try {
            Map<String, String> conditions = new LinkedHashMap<>();
            conditions.put("cond[country_nm::EQ]", countryName);

            MofaResponse<PeopleExchangeItem> response = mofaFeignClient.getPeopleExchanges(
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
    protected String dedupKey(PeopleExchangeItem item) {
        return item.countryIsoAlp2() + "-" + item.year() + "-" + item.month()
                + "-" + (item.exchangeCn() != null ? item.exchangeCn().hashCode() : 0);
    }

    @Override
    protected EvidenceDocument toEvidenceDocument(UUID claimId, ClaimCategory category,
                                                  String searchKeyword, PeopleExchangeItem item) {
        String country = notBlank(item.countryNm()) ? item.countryNm() : item.countryEngNm();
        String date = formatDate(item.year(), item.month(), item.day());

        StringBuilder content = new StringBuilder();
        if (notBlank(date)) content.append("시점: ").append(date).append("\n");
        if (notBlank(item.exchangeTy())) content.append("유형: ").append(item.exchangeTy().trim()).append("\n");
        if (notBlank(item.exchangeCn())) content.append("교류: ").append(item.exchangeCn().trim());

        return EvidenceDocument.builder()
                .claimId(claimId)
                .apiName(apiName())
                .searchKeyword(searchKeyword)
                .title(country + " 주요인사 교류" + (notBlank(date) ? " (" + date + ")" : ""))
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
