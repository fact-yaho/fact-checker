package com.yaho.factchecker.infrastructure.retrieval.feign;

import com.yaho.factchecker.infrastructure.retrieval.config.MofaFeignConfig;
import com.yaho.factchecker.infrastructure.retrieval.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 외교부(MOFA) 공공데이터 API 호출 클라이언트
 * base-url = https://apis.data.go.kr/1262000
 * API마다 서비스/오퍼레이션 경로가 달라 메서드별로 전체 경로를 지정
 * cond[...] 조건은 @SpringQueryMap 으로 전달
 */
@FeignClient(
        name = "mofaClient",
        url = "${mofa.api.base-url}",
        configuration = MofaFeignConfig.class
)
public interface MofaFeignClient {

    // 1. 우리나라와의 관계
    @GetMapping("/OverviewKorRelationService/getOverviewKorRelationList")
    MofaResponse<KorRelationItem> getKorRelations(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("returnType") String returnType,
            @SpringQueryMap Map<String, String> conditions
    );

    // 2. 북한과의 관계
    @GetMapping("/OverviewNorthKorRelationService/getOverviewNorthKorRelationList")
    MofaResponse<NorthKorRelationItem> getNorthKorRelations(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("returnType") String returnType,
            @SpringQueryMap Map<String, String> conditions
    );

    // 3. 주요정세
    @GetMapping("/OverviewSituationService/getOverviewSituationList")
    MofaResponse<SituationItem> getSituations(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("returnType") String returnType,
            @SpringQueryMap Map<String, String> conditions
    );

    // 4. 주요협정
    @GetMapping("/OverviewAgreementService/getOverviewAgreementList")
    MofaResponse<AgreementItem> getAgreements(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("returnType") String returnType,
            @SpringQueryMap Map<String, String> conditions
    );

    // 5. 무역관계
    @GetMapping("/CountryKorTradeService2/getCountryKorTradeList2")
    MofaResponse<TradeItem> getTrades(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("returnType") String returnType,
            @SpringQueryMap Map<String, String> conditions
    );

    // 6. 주요인사 교류
    @GetMapping("/PeopleExchangeService/getPeopleExchangeList")
    MofaResponse<PeopleExchangeItem> getPeopleExchanges(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("returnType") String returnType,
            @SpringQueryMap Map<String, String> conditions
    );

    // 7. 재외공관
    @GetMapping("/EmbassyService2/getEmbassyList2")
    MofaResponse<EmbassyItem> getEmbassies(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("returnType") String returnType,
            @SpringQueryMap Map<String, String> conditions
    );

    // 8. 경제현황
    @GetMapping("/OverviewEconomicService/OverviewEconomicList")
    MofaResponse<EconomicItem> getEconomics(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("returnType") String returnType,
            @SpringQueryMap Map<String, String> conditions
    );

    // 9. 기관 진출현황
    @GetMapping("/CountryOverseaAgencyService2/getCountryOverseaAgencyList2")
    MofaResponse<OverseaAgencyItem> getOverseaAgencies(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("returnType") String returnType,
            @SpringQueryMap Map<String, String> conditions
    );

    // 10. 브리핑 (검색 미지원 → 전체 페이징)
    @GetMapping("/briefingsService/getBriefings")
    MofaResponse<BriefingItem> getBriefings(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("returnType") String returnType
    );

    // 11. 연설문 (검색 미지원 → 전체 페이징)
    @GetMapping("/speechesService/getSpeeches")
    MofaResponse<SpeechItem> getSpeeches(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("returnType") String returnType
    );

    // 12. 보도자료 (검색 미지원 → 전체 페이징)
    @GetMapping("/pressRlsService/getPressRls")
    MofaResponse<PressReleaseItem> getPressReleases(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("returnType") String returnType
    );

    // 13. 국제경제동향 (검색 미지원 → 전체 페이징)
    @GetMapping("/globalEconomicTrendsService/getGlobalEconomicTrends")
    MofaResponse<GlobalEconomicTrendItem> getGlobalEconomicTrends(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows,
            @RequestParam("returnType") String returnType
    );

    // 14. 외교안보연구소 발간자료 (국가 조건 없어 전체 페이징)
    @GetMapping("/IfansPblctList1/getIfansPublctList")
    MofaResponse<IfansPublicationItem> getIfansPublications(
            @RequestParam("serviceKey") String serviceKey,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("numOfRows") int numOfRows
    );
}
