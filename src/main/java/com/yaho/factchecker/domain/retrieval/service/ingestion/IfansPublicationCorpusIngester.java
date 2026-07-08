package com.yaho.factchecker.domain.retrieval.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaho.factchecker.domain.retrieval.repository.EvidenceDocumentRepository;
import com.yaho.factchecker.domain.retrieval.service.DocumentFactWriter;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.infrastructure.retrieval.ContentCleaner;
import com.yaho.factchecker.infrastructure.retrieval.dto.IfansPublicationItem;
import com.yaho.factchecker.infrastructure.retrieval.dto.MofaResponse;
import com.yaho.factchecker.infrastructure.retrieval.feign.MofaFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 외교안보연구소(IFANS) 발간자료 코퍼스 적재기 (= 국제정세·다자외교 카테고리)
// description 이 본문, cond(국가) 없어 코퍼스 유형으로 전체 페이징 적재
@Slf4j
@Component
public class IfansPublicationCorpusIngester extends AbstractCorpusIngester<IfansPublicationItem> {

    private final MofaFeignClient mofaFeignClient;
    private final String serviceKey;

    public IfansPublicationCorpusIngester(ContentCleaner contentCleaner,
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
        return "외교부_외교안보연구소_발간자료";
    }

    @Override
    protected MofaResponse<IfansPublicationItem> fetchPage(int pageNo, int numOfRows) {
        // 발간자료 API 는 returnType 파라미터가 없음 (명세 기준)
        return mofaFeignClient.getIfansPublications(serviceKey, pageNo, numOfRows);
    }

    // 재처리용 item 타입
    @Override
    protected Class<IfansPublicationItem> itemType() {
        return IfansPublicationItem.class;
    }

    @Override
    protected ClaimCategory category() {
        // 발간자료 = 국제정세·다자외교
        return ClaimCategory.INTERNATIONAL_AFFAIRS;
    }

    @Override
    protected String getTitle(IfansPublicationItem item) {
        return item.title();
    }

    @Override
    protected String getRawContent(IfansPublicationItem item) {
        // 본문 = description (발간물 요약/내용)
        return item.description();
    }

    @Override
    protected String getPublishedAtRaw(IfansPublicationItem item) {
        // 발간일 "yyyy-MM-dd"
        return item.pubDate();
    }

    @Override
    protected String getOriginalUrl(IfansPublicationItem item) {
        // 원문 URL
        return item.dataUrl();
    }

    @Override
    protected String getAuthorOrDept(IfansPublicationItem item) {
        return item.author();
    }
}
