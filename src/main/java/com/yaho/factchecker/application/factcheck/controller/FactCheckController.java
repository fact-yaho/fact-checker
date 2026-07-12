package com.yaho.factchecker.application.factcheck.controller;

import com.yaho.factchecker.application.factcheck.service.FactCheckOrchestratorService;
import com.yaho.factchecker.application.factcheck.dto.request.FactCheckStartRequest;
import com.yaho.factchecker.domain.result.dto.AnalysisResultDetailResponse;   // 🔧 응답 타입 변경
import com.yaho.factchecker.domain.user.entity.User;
import com.yaho.factchecker.domain.user.repository.UserRepository;
import com.yaho.factchecker.global.util.config.PrincipalDetails;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/")
public class FactCheckController {

    private final FactCheckOrchestratorService factCheckOrchestratorService;
    private final UserRepository userRepository;

    @PostMapping("fact-checks")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResultDetailResponse startFactCheck(
            @Valid @RequestBody FactCheckStartRequest request,
            @AuthenticationPrincipal Object principal) {          // 비회원이면 Jwt 아님(anonymous)
        UUID userId = resolveUserId(principal);                  // 로그인 → UUID / 비회원 → null
        return factCheckOrchestratorService.start(request, userId);
    }

    // 🔧 히스토리 컨트롤러와 '동일한' 방식으로 userId 해석 (단, 비회원이면 예외 대신 null)
    private UUID resolveUserId(Object principal) {   // 네 메서드 이름에 맞춰
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
                        .orElse(null);
            }
        }
        return null; // 비회원 → 저장 시 userId=null
    }
}
