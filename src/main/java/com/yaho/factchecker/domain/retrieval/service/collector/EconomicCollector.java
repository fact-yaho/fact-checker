package com.yaho.factchecker.domain.retrieval.service.collector;

import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.global.exception.BusinessException;
import com.yaho.factchecker.global.exception.ErrorCode;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.infrastructure.retrieval.dto.EconomicItem;
import com.yaho.factchecker.infrastructure.retrieval.dto.MofaResponse;
import com.yaho.factchecker.infrastructure.retrieval.feign.MofaFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 통상·경제외교 카테고리(보조) — 국가·지역별 경제현황
@Slf4j
@Component
public class EconomicCollector extends AbstractMofaCollector<EconomicItem> {

    private final MofaFeignClient mofaFeignClient;
    private final String serviceKey;
    private final int numOfRows;

    public EconomicCollector(MofaFeignClient mofaFeignClient,
                             @Value("${mofa.api.service-key}") String serviceKey,
                             @Value("${mofa.api.num-of-rows}") int numOfRows) {
        this.mofaFeignClient = mofaFeignClient;
        this.serviceKey = serviceKey;
        this.numOfRows = numOfRows;
    }

    @Override
    public Set<ClaimCategory> supportedCategories() {
        return Set.of(ClaimCategory.ECONOMIC_DIPLOMACY);
    }

    @Override
    protected String apiName() {
        return "외교부_국가·지역별 경제현황";
    }

    @Override
    protected List<EconomicItem> fetchByCountry(String countryName) {
        try {
            Map<String, String> conditions = new LinkedHashMap<>();
            conditions.put("cond[country_nm::EQ]", countryName);

            MofaResponse<EconomicItem> response = mofaFeignClient.getEconomics(
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
    protected String dedupKey(EconomicItem item) {
        return item.countryIsoAlp2();
    }

    @Override
    protected EvidenceDocument toEvidenceDocument(UUID claimId, ClaimCategory category,
                                                  String searchKeyword, EconomicItem item) {
        String country = notBlank(item.countryNm()) ? item.countryNm() : item.countryEngNm();

        StringBuilder content = new StringBuilder();
        if (notBlank(item.gdp())) content.append("GDP(달러): ").append(item.gdp()).append("\n");
        if (notBlank(item.gdpGrowthRate())) content.append("GDP 성장률(%): ").append(item.gdpGrowthRate()).append("\n");
        if (notBlank(item.gdpPerCapita())) content.append("1인당 GDP(달러): ").append(item.gdpPerCapita()).append("\n");
        if (notBlank(item.exportAmount())) content.append("수출액(달러): ").append(item.exportAmount()).append("\n");
        if (notBlank(item.importAmount())) content.append("수입액(달러): ").append(item.importAmount()).append("\n");
        if (notBlank(item.inflationRate())) content.append("물가상승률(%): ").append(item.inflationRate()).append("\n");
        if (notBlank(item.unemploymentRate())) content.append("실업률(%): ").append(item.unemploymentRate()).append("\n");
        if (notBlank(item.currencyUnit())) content.append("통화: ").append(item.currencyUnit()).append("\n");
        if (notBlank(item.mainResource())) content.append("주요자원: ").append(item.mainResource().trim()).append("\n");
        if (notBlank(item.majorIndustry())) content.append("주요산업: ").append(item.majorIndustry().trim());

        return EvidenceDocument.builder()
                .claimId(claimId)
                .apiName(apiName())
                .searchKeyword(searchKeyword)
                .title(country + " 경제현황")
                .contentCleaned(content.toString().trim())
                .categoryName(category)
                .build();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
