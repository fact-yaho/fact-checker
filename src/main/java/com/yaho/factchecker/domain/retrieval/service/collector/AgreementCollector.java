package com.yaho.factchecker.domain.retrieval.service.collector;

import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.global.exception.BusinessException;
import com.yaho.factchecker.global.exception.ErrorCode;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.infrastructure.retrieval.dto.AgreementItem;
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

// 조약·협정 카테고리 — 국가·지역별 주요협정 정보
@Slf4j
@Component
public class AgreementCollector extends AbstractMofaCollector<AgreementItem> {

    private final MofaFeignClient mofaFeignClient;
    private final String serviceKey;
    private final int numOfRows;

    public AgreementCollector(MofaFeignClient mofaFeignClient,
                              @Value("${mofa.api.service-key}") String serviceKey,
                              @Value("${mofa.api.num-of-rows}") int numOfRows) {
        this.mofaFeignClient = mofaFeignClient;
        this.serviceKey = serviceKey;
        this.numOfRows = numOfRows;
    }

    @Override
    public Set<ClaimCategory> supportedCategories() {
        return Set.of(ClaimCategory.TREATY_AGREEMENT);
    }

    @Override
    protected String apiName() {
        return "외교부_국가·지역별 주요협정 정보";
    }

    @Override
    protected List<AgreementItem> fetchByCountry(String countryName) {
        try {
            Map<String, String> conditions = new LinkedHashMap<>();
            conditions.put("cond[country_nm::EQ]", countryName);

            MofaResponse<AgreementItem> response = mofaFeignClient.getAgreements(
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
    protected String dedupKey(AgreementItem item) {
        return item.countryIsoAlp2() + "-" + item.year() + "-" + item.month()
                + "-" + (item.agreementCn() != null ? item.agreementCn().hashCode() : 0);
    }

    @Override
    protected EvidenceDocument toEvidenceDocument(UUID claimId, ClaimCategory category,
                                                  String searchKeyword, AgreementItem item) {
        String country = notBlank(item.countryNm()) ? item.countryNm() : item.countryEngNm();
        String date = formatDate(item.year(), item.month(), item.day());

        StringBuilder content = new StringBuilder();
        if (notBlank(date)) content.append("체결시점: ").append(date).append("\n");
        if (notBlank(item.agreementCn())) content.append("협정: ").append(item.agreementCn().trim());

        return EvidenceDocument.builder()
                .claimId(claimId)
                .apiName(apiName())
                .searchKeyword(searchKeyword)
                .title("대한민국과 " + country + "의 협정" + (notBlank(date) ? " (" + date + ")" : ""))
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
