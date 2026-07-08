package com.yaho.factchecker.domain.result.controller;

import com.yaho.factchecker.domain.result.dto.AnalysisResultDetailResponse;
import com.yaho.factchecker.domain.result.dto.AnalysisResultSummaryResponse;
import com.yaho.factchecker.domain.result.service.AnalysisResultReadService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/results")
public class AnalysisResultHistoryController {

    private final AnalysisResultReadService analysisResultReadService;

    /**
     * 기록 탭 목록 조회
     *
     * 화면 예시:
     * - 질문 요약
     * - 신뢰도 점수
     * - 판정
     * - 생성 일시
     */
    @GetMapping("/histories")
    public ResponseEntity<List<AnalysisResultSummaryResponse>> getMyResultHistories(
        @AuthenticationPrincipal Object principal,
        @PageableDefault(
            size = 10,
            sort = "createdAt",
            direction = Sort.Direction.DESC
        ) Pageable pageable
    ) {
        UUID userId = extractUserId(principal);

        List<AnalysisResultSummaryResponse> responses =
            analysisResultReadService.findUserResultSummaries(userId, pageable);

        return ResponseEntity.ok(responses);
    }

    /**
     * 기록 탭 단건 상세 조회
     *
     * 목록에서 하나의 결과를 클릭했을 때 사용합니다.
     * 결과 본문, 점수 상세, 근거 목록을 함께 반환합니다.
     */
    @GetMapping("/histories/{resultId}")
    public ResponseEntity<AnalysisResultDetailResponse> getMyResultDetail(
        @AuthenticationPrincipal Object principal,
        @PathVariable UUID resultId
    ) {
        UUID userId = extractUserId(principal);

        AnalysisResultDetailResponse response =
            analysisResultReadService.getUserResultDetail(userId, resultId);

        return ResponseEntity.ok(response);
    }

    private UUID extractUserId(Object principal) {
        if (principal instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString("user_id");

            if (userId == null || userId.isBlank()) {
                userId = jwt.getClaimAsString("service_user_id");
            }

            if (userId == null || userId.isBlank()) {
                throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "토큰 내 사용자 ID 정보가 유효하지 않습니다."
                );
            }

            try {
                return UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "토큰 내 사용자 ID 형식이 올바르지 않습니다."
                );
            }
        }

        throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "인증 정보가 올바르지 않습니다."
        );
    }
}
