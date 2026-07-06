package com.yaho.factchecker.domain.retrieval.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaho.factchecker.domain.retrieval.repository.EvidenceDocumentRepository;
import com.yaho.factchecker.domain.retrieval.service.DocumentFactWriter;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.infrastructure.retrieval.ContentCleaner;
import com.yaho.factchecker.infrastructure.retrieval.dto.BriefingItem;
import com.yaho.factchecker.infrastructure.retrieval.dto.MofaResponse;
import com.yaho.factchecker.infrastructure.retrieval.feign.MofaFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 브리핑 코퍼스 적재기 (= 공식입장·브리핑·논평 카테고리)
@Slf4j
@Component
public class BriefingCorpusIngester extends AbstractCorpusIngester<BriefingItem> {

    private final MofaFeignClient mofaFeignClient;
    private final String serviceKey;

    public BriefingCorpusIngester(ContentCleaner contentCleaner,
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
        // 브리핑 (보도자료와 별개)
        return "외교부_보도자료_브리핑";
    }

    @Override
    protected MofaResponse<BriefingItem> fetchPage(int pageNo, int numOfRows) {
        return mofaFeignClient.getBriefings(serviceKey, pageNo, numOfRows, "JSON");
    }

    // 재처리용 item 타입
    @Override
    protected Class<BriefingItem> itemType() {
        return BriefingItem.class;
    }

    @Override
    protected ClaimCategory category() {
        // 브리핑 = 공식 입장·브리핑·논평
        return ClaimCategory.OFFICIAL_POSITION;
    }

    @Override
    protected String getTitle(BriefingItem item) {
        return item.title();
    }

    @Override
    protected String getRawContent(BriefingItem item) {
        return item.content();
    }

    @Override
    protected String getPublishedAtRaw(BriefingItem item) {
        // "2007-04-18 09:00:00.000" → 베이스에서 앞 10자 파싱
        return item.updtDate();
    }

    @Override
    protected String getOriginalUrl(BriefingItem item) {
        // 대개 null
        return item.fileUrl();
    }

    @Override
    protected String getAuthorOrDept(BriefingItem item) {
        // 대개 null
        return item.creator();
    }
}
