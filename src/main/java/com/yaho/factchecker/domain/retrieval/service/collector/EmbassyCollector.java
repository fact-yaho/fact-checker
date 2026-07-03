package com.yaho.factchecker.domain.retrieval.service.collector;

import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.global.exception.BusinessException;
import com.yaho.factchecker.global.exception.ErrorCode;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.infrastructure.retrieval.dto.EmbassyItem;
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

// 양자관계 카테고리(보조) — 국가·지역별 재외공관 정보
@Slf4j
@Component
public class EmbassyCollector extends AbstractMofaCollector<EmbassyItem> {

    private final MofaFeignClient mofaFeignClient;
    private final String serviceKey;
    private final int numOfRows;

    public EmbassyCollector(MofaFeignClient mofaFeignClient,
                            @Value("${mofa.api.service-key}") String serviceKey,
                            @Value("${mofa.api.num-of-rows}") int numOfRows) {
        this.mofaFeignClient = mofaFeignClient;
        this.serviceKey = serviceKey;
        this.numOfRows = numOfRows;
    }

    @Override
    public Set<ClaimCategory> supportedCategories() {
        return Set.of(ClaimCategory.BILATERAL_RELATIONS);
    }

    @Override
    protected String apiName() {
        return "외교부_국가·지역별 재외공관 정보";
    }

    @Override
    protected List<EmbassyItem> fetchByCountry(String countryName) {
        try {
            Map<String, String> conditions = new LinkedHashMap<>();
            conditions.put("cond[country_nm::EQ]", countryName);

            MofaResponse<EmbassyItem> response = mofaFeignClient.getEmbassies(
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
    protected String dedupKey(EmbassyItem item) {
        return item.countryIsoAlp2() + "-" + item.embassyKorNm();
    }

    @Override
    protected EvidenceDocument toEvidenceDocument(UUID claimId, ClaimCategory category,
                                                  String searchKeyword, EmbassyItem item) {
        String country = notBlank(item.countryNm()) ? item.countryNm() : item.countryEngNm();

        StringBuilder content = new StringBuilder();
        if (notBlank(item.embassyKorNm())) content.append("공관: ").append(item.embassyKorNm().trim()).append("\n");
        if (notBlank(item.embassyTyCdNm())) content.append("유형: ").append(item.embassyTyCdNm().trim()).append("\n");
        if (notBlank(item.emblgbdAddr())) content.append("주소: ").append(item.emblgbdAddr().trim()).append("\n");
        if (notBlank(item.telNo())) content.append("전화: ").append(item.telNo().trim());

        return EvidenceDocument.builder()
                .claimId(claimId)
                .apiName(apiName())
                .searchKeyword(searchKeyword)
                .title(country + " 재외공관 정보")
                .contentCleaned(content.toString().trim())
                .categoryName(category)
                .build();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
