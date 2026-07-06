package com.yaho.factchecker.domain.retrieval.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaho.factchecker.domain.retrieval.repository.EvidenceDocumentRepository;
import com.yaho.factchecker.domain.retrieval.service.DocumentFactWriter;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.infrastructure.retrieval.ContentCleaner;
import com.yaho.factchecker.infrastructure.retrieval.dto.GlobalEconomicTrendItem;
import com.yaho.factchecker.infrastructure.retrieval.dto.MofaResponse;
import com.yaho.factchecker.infrastructure.retrieval.feign.MofaFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 국제경제동향 코퍼스 적재기 (= 통상·경제외교 카테고리)
// ('경제현황'(EconomicItem)과는 다른 API — 검색 미지원 API)
@Slf4j
@Component
public class GlobalEconomicTrendCorpusIngester extends AbstractCorpusIngester<GlobalEconomicTrendItem> {

    private final MofaFeignClient mofaFeignClient;
    private final String serviceKey;

    public GlobalEconomicTrendCorpusIngester(ContentCleaner contentCleaner,
                                             DocumentFactWriter documentFactWriter,
                                             IngestionFailureRecorder failureRecorder,
                                             ObjectMapper objectMapper,
                                             EvidenceDocumentRepository evidenceDocumentRepository,
                                             MofaFeignClient mofaFeignClient,
                                             @Value("${mofa.api.service-key}") String serviceKey) {
        super(contentCleaner, documentFactWriter, failureRecorder, objectMapper, evidenceDocumentRepository);
        this.mofaFeignClient = mofaFeignClient;
        this.serviceKey = serviceKey;
    }

    @Override
    public String apiName() {
        return "외교부_국제경제동향";
    }

    @Override
    protected MofaResponse<GlobalEconomicTrendItem> fetchPage(int pageNo, int numOfRows) {
        return mofaFeignClient.getGlobalEconomicTrends(serviceKey, pageNo, numOfRows, "JSON");
    }

    // 재처리용 item 타입
    @Override
    protected Class<GlobalEconomicTrendItem> itemType() {
        return GlobalEconomicTrendItem.class;
    }

    @Override
    protected ClaimCategory category() {
        // 국제경제동향 = 통상·경제외교
        return ClaimCategory.ECONOMIC_DIPLOMACY;
    }

    @Override
    protected String getTitle(GlobalEconomicTrendItem item) {
        return item.title();
    }

    @Override
    protected String getRawContent(GlobalEconomicTrendItem item) {
        return item.content();
    }

    @Override
    protected String getPublishedAtRaw(GlobalEconomicTrendItem item) {
        return item.updtDate();
    }

    @Override
    protected String getOriginalUrl(GlobalEconomicTrendItem item) {
        return item.fileUrl();
    }

    @Override
    protected String getAuthorOrDept(GlobalEconomicTrendItem item) {
        return item.creator();
    }
}
