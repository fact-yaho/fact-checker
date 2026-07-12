package com.yaho.factchecker.domain.result.controller;

import com.yaho.factchecker.domain.result.dto.AnalysisResultDetailResponse;
import com.yaho.factchecker.domain.result.dto.AnalysisResultSummaryResponse;
import com.yaho.factchecker.domain.result.service.AnalysisResultReadService;
import com.yaho.factchecker.domain.result.service.AnalysisResultService;
import com.yaho.factchecker.domain.user.entity.User;
import com.yaho.factchecker.domain.user.repository.UserRepository;
import com.yaho.factchecker.global.util.config.PrincipalDetails;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final UserRepository userRepository;
    private final AnalysisResultService analysisResultService;

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

    @DeleteMapping("/histories/{id}")
    public ResponseEntity<Void> deleteMyResult(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = extractUserId(jwt);
        try {
            analysisResultService.deleteByUser(id, userId);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

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
        if (principal instanceof PrincipalDetails principalDetails) {
            return principalDetails.getUser().getId();
        }
        if (principal instanceof Jwt jwt) {
            String email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank()) {
                email = jwt.getClaimAsString("preferred_username");
            }
            if (email != null && !email.isBlank()) {
                return userRepository.findByEmail(email)
                        .map(User::getId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED, "존재하지 않는 회원입니다."));
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
}
