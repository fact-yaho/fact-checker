package com.yaho.factchecker.domain.retrieval.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaho.factchecker.domain.retrieval.repository.EvidenceDocumentRepository;
import com.yaho.factchecker.domain.retrieval.service.DocumentFactWriter;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.infrastructure.retrieval.ContentCleaner;
import com.yaho.factchecker.infrastructure.retrieval.dto.MofaResponse;
import com.yaho.factchecker.infrastructure.retrieval.dto.PressReleaseItem;
import com.yaho.factchecker.infrastructure.retrieval.feign.MofaFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 보도자료 코퍼스 적재기 (= 공식입장·브리핑·논평 카테고리)
@Slf4j
@Component
public class PressReleaseCorpusIngester extends AbstractCorpusIngester<PressReleaseItem> {

    private final MofaFeignClient mofaFeignClient;
    private final String serviceKey;

    public PressReleaseCorpusIngester(ContentCleaner contentCleaner,
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
        return "외교부_보도자료";
    }

    @Override
    protected MofaResponse<PressReleaseItem> fetchPage(int pageNo, int numOfRows) {
        return mofaFeignClient.getPressReleases(serviceKey, pageNo, numOfRows, "JSON");
    }

    // 재처리용 item 타입
    @Override
    protected Class<PressReleaseItem> itemType() {
        return PressReleaseItem.class;
    }

    @Override
    protected ClaimCategory category() {
        // 보도자료 = 공식 입장·브리핑·논평
        return ClaimCategory.OFFICIAL_POSITION;
    }

    @Override
    protected String getTitle(PressReleaseItem item) {
        return item.title();
    }

    @Override
    protected String getRawContent(PressReleaseItem item) {
        return item.content();
    }

    @Override
    protected String getPublishedAtRaw(PressReleaseItem item) {
        return item.updtDate();
    }

    @Override
    protected String getOriginalUrl(PressReleaseItem item) {
        return item.fileUrl();
    }

    @Override
    protected String getAuthorOrDept(PressReleaseItem item) {
        return item.creator();
    }
}
