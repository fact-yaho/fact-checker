package com.yaho.factchecker.domain.retrieval.service.ingestion;

import com.yaho.factchecker.application.ai.port.FactExtractionPort;
import com.yaho.factchecker.domain.retrieval.repository.DocumentFactRepository;
import com.yaho.factchecker.domain.retrieval.repository.EvidenceDocumentRepository;
import com.yaho.factchecker.domain.retrieval.service.TextEmbedder;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.infrastructure.retrieval.ContentCleaner;
import com.yaho.factchecker.infrastructure.retrieval.dto.MofaResponse;
import com.yaho.factchecker.infrastructure.retrieval.dto.SpeechItem;
import com.yaho.factchecker.infrastructure.retrieval.feign.MofaFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

// 연설문 코퍼스 적재기 (= 외교 정책·기조 카테고리)
@Slf4j
@Component
public class SpeechCorpusIngester extends AbstractCorpusIngester<SpeechItem> {

    private final MofaFeignClient mofaFeignClient;
    private final String serviceKey;

    public SpeechCorpusIngester(EvidenceDocumentRepository evidenceDocumentRepository,
                                DocumentFactRepository documentFactRepository,
                                ContentCleaner contentCleaner,
                                FactExtractionPort factExtractionPort,
                                TextEmbedder textEmbedder,
                                MofaFeignClient mofaFeignClient,
                                @Value("${mofa.api.service-key}") String serviceKey) {
        super(evidenceDocumentRepository, documentFactRepository, contentCleaner,
                factExtractionPort, textEmbedder);
        this.mofaFeignClient = mofaFeignClient;
        this.serviceKey = serviceKey;
    }

    @Override
    public String apiName() {
        return "외교부_연설문";
    }

    @Override
    protected List<SpeechItem> fetchPage(int pageNo, int numOfRows) {
        try {
            MofaResponse<SpeechItem> response =
                    mofaFeignClient.getSpeeches(serviceKey, pageNo, numOfRows, "JSON");
            if (!response.isSuccess()) {
                log.warn("[{}] API 실패 응답 (page={})", apiName(), pageNo);
                return List.of();
            }
            return response.items();
        } catch (Exception e) {
            log.error("[{}] API 호출 실패 (page={})", apiName(), pageNo, e);
            return List.of();
        }
    }

    @Override
    protected ClaimCategory category() {
        // 연설문 = 외교 정책·기조
        return ClaimCategory.DIPLOMATIC_POLICY;
    }

    @Override
    protected String getTitle(SpeechItem item) {
        return item.title();
    }

    @Override
    protected String getRawContent(SpeechItem item) {
        return item.content();
    }

    @Override
    protected String getPublishedAtRaw(SpeechItem item) {
        // "2007-04-03"
        return item.updtDate();
    }

    @Override
    protected String getOriginalUrl(SpeechItem item) {
        return item.fileUrl();
    }

    @Override
    protected String getAuthorOrDept(SpeechItem item) {
        // 연설자 (예: "장관") — creator 대신 speecher
        return item.speecher();
    }
}
