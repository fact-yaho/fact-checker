package com.yaho.factchecker.domain.retrieval.service.collector;

import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.global.exception.BusinessException;
import com.yaho.factchecker.global.exception.ErrorCode;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.infrastructure.retrieval.dto.MofaResponse;
import com.yaho.factchecker.infrastructure.retrieval.dto.NorthKorRelationItem;
import com.yaho.factchecker.infrastructure.retrieval.feign.MofaFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 대북관계 카테고리 — 국가·지역별 북한과의 관계
@Slf4j
@Component
public class NorthKorRelationCollector extends AbstractMofaCollector<NorthKorRelationItem> {

    private final MofaFeignClient mofaFeignClient;
    private final String serviceKey;
    private final int numOfRows;

    public NorthKorRelationCollector(MofaFeignClient mofaFeignClient,
                                     @Value("${mofa.api.service-key}") String serviceKey,
                                     @Value("${mofa.api.num-of-rows}") int numOfRows) {
        this.mofaFeignClient = mofaFeignClient;
        this.serviceKey = serviceKey;
        this.numOfRows = numOfRows;
    }

    @Override
    public Set<ClaimCategory> supportedCategories() {
        return Set.of(ClaimCategory.NORTH_KOREA_RELATIONS);
    }

    @Override
    protected String apiName() {
        return "외교부_국가·지역별 북한과의 관계";
    }

    @Override
    protected List<NorthKorRelationItem> fetchByCountry(String countryName) {
        try {
            Map<String, String> conditions = new LinkedHashMap<>();
            conditions.put("cond[country_nm::EQ]", countryName);

            MofaResponse<NorthKorRelationItem> response = mofaFeignClient.getNorthKorRelations(
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
    protected String dedupKey(NorthKorRelationItem item) {
        return item.countryIsoAlp2();
    }

    @Override
    protected EvidenceDocument toEvidenceDocument(UUID claimId, ClaimCategory category,
                                                  String searchKeyword, NorthKorRelationItem item) {
        String country = notBlank(item.countryNm()) ? item.countryNm() : item.countryEngNm();
        StringBuilder sb = new StringBuilder();
        append(sb, "북한과의 외교관계", item.diplomaticRelations());
        append(sb, "공관현황", item.emblgbdStatus());

        return EvidenceDocument.builder()
                .claimId(claimId)
                .apiName(apiName())
                .searchKeyword(searchKeyword)
                .title(country + "와(과) 북한의 관계")
                .contentCleaned(sb.toString().trim())
                .categoryName(category)
                .build();
    }

    private void append(StringBuilder sb, String label, String value) {
        if (notBlank(value)) sb.append(label).append(": ").append(value.trim()).append("\n");
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
