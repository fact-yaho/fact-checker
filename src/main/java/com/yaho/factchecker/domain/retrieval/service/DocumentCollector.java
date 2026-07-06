package com.yaho.factchecker.domain.retrieval.service;

import com.yaho.factchecker.domain.retrieval.dto.ClaimRetrievalRequest;
import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import com.yaho.factchecker.domain.retrieval.repository.EvidenceDocumentRepository;
import com.yaho.factchecker.domain.retrieval.service.collector.ApiDocumentCollector;
import com.yaho.factchecker.global.type.ClaimCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 근거문서 수집 오케스트레이터 (라우터)
 * 소주장 카테고리에 해당하는 수집기(ApiDocumentCollector)들만 골라 실행하고,
 * 결과를 모아 evidence_document 에 일괄저장
 *
 * 카테고리 → API 매핑은 각 수집기의 supportedCategories() 에 분산 정의(하드코딩)
 * 검색 안 되는 API(A부류: 보도자료/브리핑/연설문/업무계획)가 필요한 카테고리
 * (공식입장·브리핑·논평 / 외교정책·기조)는 담당 수집기가 없어 결과가 비며, 추후 추가 예정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentCollector {

    // 스프링이 ApiDocumentCollector 구현체를 전부 주입
    private final List<ApiDocumentCollector> collectors;
    private final EvidenceDocumentRepository evidenceDocumentRepository;

    public void collectAndSave(ClaimRetrievalRequest request) {
        UUID claimId = request.claimId();
        ClaimCategory category = request.category();

        if (category == null) {
            log.info("카테고리 없음 → 수집 스킵. claimId={}", claimId);
            return;
        }

        // 이 카테고리를 담당하는 수집기만 선택
        List<ApiDocumentCollector> matched = collectors.stream()
                .filter(c -> c.supportedCategories().contains(category))
                .toList();

        if (matched.isEmpty()) {
            log.info("카테고리 [{}] 담당 수집기 없음 → 수집 스킵. claimId={}", category, claimId);
            return;
        }

        // 매칭된 수집기들 실행 → 결과 합침
        List<EvidenceDocument> collected = new ArrayList<>();
        for (ApiDocumentCollector collector : matched) {
            collected.addAll(collector.collect(request));
        }

        if (collected.isEmpty()) {
            log.info("수집 결과 없음. claimId={}, category={}", claimId, category);
            return;
        }

        evidenceDocumentRepository.saveAll(collected);
        log.info("총 {}건 저장. claimId={}, category={}, 수집기={}개",
                collected.size(), claimId, category, matched.size());
    }
}
