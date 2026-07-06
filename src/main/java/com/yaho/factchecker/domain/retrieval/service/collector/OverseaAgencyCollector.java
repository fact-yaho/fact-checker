package com.yaho.factchecker.domain.retrieval.service.collector;

import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.global.exception.BusinessException;
import com.yaho.factchecker.global.exception.ErrorCode;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.infrastructure.retrieval.dto.MofaResponse;
import com.yaho.factchecker.infrastructure.retrieval.dto.OverseaAgencyItem;
import com.yaho.factchecker.infrastructure.retrieval.feign.MofaFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 국제정세·다자외교 + 개발협력·ODA 카테고리(보조) — 국가·지역별 우리나라기관 진출현황
@Slf4j
@Component
public class OverseaAgencyCollector extends AbstractMofaCollector<OverseaAgencyItem> {

    private final MofaFeignClient mofaFeignClient;
    private final String serviceKey;
    private final int numOfRows;

    public OverseaAgencyCollector(MofaFeignClient mofaFeignClient,
                                  @Value("${mofa.api.service-key}") String serviceKey,
                                  @Value("${mofa.api.num-of-rows}") int numOfRows) {
        this.mofaFeignClient = mofaFeignClient;
        this.serviceKey = serviceKey;
        this.numOfRows = numOfRows;
    }

    @Override
    public Set<ClaimCategory> supportedCategories() {
        return Set.of(ClaimCategory.INTERNATIONAL_AFFAIRS, ClaimCategory.DEVELOPMENT_COOPERATION);
    }

    @Override
    protected String apiName() {
        return "외교부_국가·지역별 우리나라기관 진출현황";
    }

    @Override
    protected List<OverseaAgencyItem> fetchByCountry(String countryName) {
        try {
            Map<String, String> conditions = new LinkedHashMap<>();
            conditions.put("cond[country_nm::EQ]", countryName);

            MofaResponse<OverseaAgencyItem> response = mofaFeignClient.getOverseaAgencies(
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
    protected String dedupKey(OverseaAgencyItem item) {
        return item.countryIsoAlp2() + "-" + item.agencyNm() + "-" + item.writtenYear();
    }

    @Override
    protected EvidenceDocument toEvidenceDocument(UUID claimId, ClaimCategory category,
                                                  String searchKeyword, OverseaAgencyItem item) {
        String country = notBlank(item.countryNm()) ? item.countryNm() : item.countryEngNm();

        StringBuilder content = new StringBuilder();
        if (item.writtenYear() != null) content.append("작성연도: ").append(item.writtenYear()).append("\n");
        if (notBlank(item.agencyNm())) content.append("기관명: ").append(item.agencyNm().trim()).append("\n");
        if (notBlank(item.agencyTyCdNm())) content.append("기관유형: ").append(item.agencyTyCdNm().trim()).append("\n");
        if (notBlank(item.agencyAdvncCn())) content.append("진출내용: ").append(item.agencyAdvncCn().trim());

        return EvidenceDocument.builder()
                .claimId(claimId)
                .apiName(apiName())
                .searchKeyword(searchKeyword)
                .title(country + " 우리나라기관 진출현황")
                .contentCleaned(content.toString().trim())
                .categoryName(category)
                .build();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
