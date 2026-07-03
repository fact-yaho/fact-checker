package com.yaho.factchecker.domain.retrieval.service.collector;

import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.global.exception.BusinessException;
import com.yaho.factchecker.global.exception.ErrorCode;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.infrastructure.retrieval.dto.MofaResponse;
import com.yaho.factchecker.infrastructure.retrieval.dto.TradeItem;
import com.yaho.factchecker.infrastructure.retrieval.feign.MofaFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 통상·경제외교 카테고리 — 국가·지역별 우리나라와의 무역관계
@Slf4j
@Component
public class TradeCollector extends AbstractMofaCollector<TradeItem> {

    private final MofaFeignClient mofaFeignClient;
    private final String serviceKey;
    private final int numOfRows;

    public TradeCollector(MofaFeignClient mofaFeignClient,
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
        return "외교부_국가·지역별 우리나라와의 무역관계";
    }

    @Override
    protected List<TradeItem> fetchByCountry(String countryName) {
        try {
            Map<String, String> conditions = new LinkedHashMap<>();
            conditions.put("cond[country_nm::EQ]", countryName);

            MofaResponse<TradeItem> response = mofaFeignClient.getTrades(
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
    protected String dedupKey(TradeItem item) {
        return item.countryIsoAlp2() + "-" + item.ytTradeYear();
    }

    @Override
    protected EvidenceDocument toEvidenceDocument(UUID claimId, ClaimCategory category,
                                                  String searchKeyword, TradeItem item) {
        String country = notBlank(item.countryNm()) ? item.countryNm() : item.countryEngNm();

        StringBuilder content = new StringBuilder();
        if (notBlank(item.ytTradeYear())) content.append("교역연도: ").append(item.ytTradeYear()).append("\n");
        if (item.ytExportAmount() != null) content.append("수출액(달러): ").append(item.ytExportAmount()).append("\n");
        if (item.ytIncomeAmount() != null) content.append("수입액(달러): ").append(item.ytIncomeAmount()).append("\n");
        if (notBlank(item.exportCn())) content.append("주요 수출품: ").append(item.exportCn().trim()).append("\n");
        if (notBlank(item.incomeCn())) content.append("주요 수입품: ").append(item.incomeCn().trim());

        return EvidenceDocument.builder()
                .claimId(claimId)
                .apiName(apiName())
                .searchKeyword(searchKeyword)
                .title("대한민국과 " + country + "의 무역관계"
                        + (notBlank(item.ytTradeYear()) ? " (" + item.ytTradeYear() + ")" : ""))
                .contentCleaned(content.toString().trim())
                .categoryName(category)
                .build();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
