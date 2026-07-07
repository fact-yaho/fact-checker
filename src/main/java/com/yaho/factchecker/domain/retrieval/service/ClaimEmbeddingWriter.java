package com.yaho.factchecker.domain.retrieval.service;

import com.yaho.factchecker.domain.retrieval.entity.Category;
import com.yaho.factchecker.domain.retrieval.entity.ClaimEmbedding;
import com.yaho.factchecker.domain.retrieval.repository.CategoryRepository;
import com.yaho.factchecker.domain.retrieval.repository.ClaimEmbeddingRepository;
import com.yaho.factchecker.global.type.ClaimCategory;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소주장 임베딩 저장 전용 빈 (독립 트랜잭션)
 * 캐시 미스로 새로 검색한 소주장을 claim_embedding에 저장
 *
 * retrieveEvidence의 메인 트랜잭션과 분리(REQUIRES_NEW)하여,
 * 이 저장의 실패가 재정렬 결과 저장(핵심)을 롤백시키지 않도록 함
 *
 * (별도 빈이라 스프링 프록시를 거쳐 트랜잭션 전파가 실제로 적용됨 — self-invocation 회피)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimEmbeddingWriter {

    private final ClaimEmbeddingRepository claimEmbeddingRepository;
    private final CategoryRepository categoryRepository;

    /**
     * 소주장 임베딩 저장 (미스 시에만 호출)
     * 독립 트랜잭션 — 저장 실패는 이 트랜잭션만 롤백되고 호출측(메인)에는 전파되지 않도록
     * 호출측에서 예외를 잡음(아래 save는 예외를 던질 수 있음)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(UUID claimId, ClaimCategory categoryEnum, String claimText, float[] claimVector) {
        // 이미 저장돼 있으면 스킵 (동일 claimId 재요청 등)
        if (claimEmbeddingRepository.findByClaimId(claimId).isPresent()) {
            return;
        }

        Category category = categoryRepository.findByCategoryName(categoryEnum)
                .orElse(null);
        if (category == null) {
            log.warn("[캐시 저장] category 매핑 없음 → claim_embedding 저장 스킵. category={}, claimId={}",
                    categoryEnum, claimId);
            return;
        }

        ClaimEmbedding embedding = ClaimEmbedding.builder()
                .category(category)
                .claimId(claimId)
                .claimText(claimText)
                .claimVector(claimVector)
                .build();
        claimEmbeddingRepository.save(embedding);
        log.info("[캐시 저장] claim_embedding 저장 완료. claimId={}", claimId);
    }
}
