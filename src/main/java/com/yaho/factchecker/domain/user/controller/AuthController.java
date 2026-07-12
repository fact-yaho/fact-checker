package com.yaho.factchecker.domain.user.controller;

import com.yaho.factchecker.domain.user.dto.request.LoginRequest;
import com.yaho.factchecker.domain.user.dto.response.LoginResponse;
import com.yaho.factchecker.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        log.info("🔑 일반 로그인 요청 수신: {}", request.getEmail());
        try {
            LoginResponse response = userService.login(request);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // 🔒 자격 증명 실패.
            //    UserService 가 계정 열거 방지를 위해 "이메일 또는 비밀번호가 올바르지 않습니다." 라는
            //    통일된 문구만 던지므로, 여기서 그대로 내려도 어떤 계정이 존재하는지 드러나지 않습니다.
            log.warn("⚠️ 로그인 인증 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());

        } catch (IllegalStateException e) {
            // 🆕 인증 서버(Keycloak) 장애 · 네트워크 오류 → 사용자 잘못이 아니므로 401 과 구분해서 503.
            //    (이걸 401 "비밀번호 틀림" 으로 처리하면 서버가 죽어도 사용자는 비밀번호만 계속 다시 칩니다.)
            log.error("❌ 인증 서버 장애: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());

        } catch (Exception e) {
            log.error("❌ 로그인 처리 중 서버 에러 발생: {}", e.getMessage(), e); // 원문은 로그에만
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류가 발생했습니다.");
        }
    }
}
