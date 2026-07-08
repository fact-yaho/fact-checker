package com.yaho.factchecker.domain.user.controller;

import com.yaho.factchecker.domain.user.dto.request.SignUpRequest;
import com.yaho.factchecker.domain.user.dto.response.MyPageResponse;
import com.yaho.factchecker.domain.user.entity.User;
import com.yaho.factchecker.domain.user.repository.UserRepository;
import com.yaho.factchecker.domain.user.service.EmailService;
import com.yaho.factchecker.domain.user.service.UserService;
import com.yaho.factchecker.global.util.config.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final EmailService emailService;


    // 1. 회원가입 API
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignUpRequest request) {
            UUID userId = userService.signUp(request);
        return ResponseEntity.ok("회원가입 완료. 유저 ID: " + userId);
    }

    // 2. 회원탈퇴 API
    @DeleteMapping
    public ResponseEntity<String> deleteUser(@AuthenticationPrincipal Object principal) {
        String currentUserEmail = extractEmail(principal);
        userService.deleteUser(currentUserEmail);
        return ResponseEntity.ok("회원탈퇴 완료. 유저 ID: " + currentUserEmail);
    }

    // 3. 이메일 중복체크 API (JSON Body를 받도록 @RequestBody로 변경)
    @PostMapping("/check-email")
    public ResponseEntity<String> checkEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("이메일 값이 비어있습니다.");
        }

        String cleanEmail = email.replace("\"", "").trim();
        boolean isDuplicate = userService.checkEmailDuplicate(cleanEmail);

        if (isDuplicate) {
            return ResponseEntity.badRequest().body("이미 존재하는 이메일입니다.");
        }
        return ResponseEntity.ok("사용 가능한 이메일입니다.");
    }

    // 4. 닉네임 중복체크 API (JSON Body를 받도록 @RequestBody로 변경)
    @PostMapping("/check-nickname")
    public ResponseEntity<String> checkNickname(@RequestBody Map<String, String> request) {
        String nickname = request.get("nickname");
        if (nickname == null || nickname.isBlank()) {
            return ResponseEntity.badRequest().body("닉네임 값이 비어있습니다.");
        }

        String cleanNickname = nickname.replace("\"", "").trim();
        boolean isDuplicate = userService.checkNicknameDuplicate(cleanNickname);

        if (isDuplicate) {
            return ResponseEntity.badRequest().body("이미 존재하는 닉네임입니다.");
        }
        return ResponseEntity.ok("사용 가능한 닉네임입니다!.");
    }

    // 이메일 인증코드 발송 api
    @PostMapping("/send-verification")
    public ResponseEntity<String> sendVerificationCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("이메일 값이 비어있습니다.");
        }

        try {
            emailService.sendVerificationEmail(email);
            return ResponseEntity.ok("네이버 메일로 인증 코드가 발송되었습니다. 메일함을 확인하세요!");
        } catch (Exception e) {
            log.error("이메일 인증 코드 발송 중 서버 에러 발생: ", e);
            return ResponseEntity.internalServerError().body("메일 발송 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

        // 이메일인증코드 검증 api
    @PostMapping("/verify-code")
    public ResponseEntity<String> verifyCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");

        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body("이메일 또는 인증 코드가 누락되었습니다.");
        }

        // 이메일 서비스의 메모리 맵에 조회를 요청합니다.
        boolean isVerified = emailService.verifyCode(email, code);

        if (isVerified) {
            return ResponseEntity.ok("이메일 인증에 성공했습니다.");
        }
        return ResponseEntity.badRequest().body("인증 코드가 올바르지 않거나 만료되었습니다.");
    }


    // 5. 내 정보 조회 API (/me)
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal Object principal) {

        final String finalEmail;

        if (principal instanceof Jwt jwt) {
            finalEmail = jwt.getClaimAsString("email");

        }

        else if (principal instanceof PrincipalDetails principalDetails) {
            finalEmail = principalDetails.getUser().getEmail();

        }
        else if (principal instanceof UserDetails userDetails) {
            finalEmail = userDetails.getUsername();
        }
        else {
            return ResponseEntity.status(401).body("인증 정보가 올바르지 않습니다.");
        }

        User user = userRepository.findByEmail(finalEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다: " + finalEmail));

        return ResponseEntity.ok(new MyPageResponse(user));
    }

     //다양한 인증 방식(JWT/세션)에서 안전하게 이메일을 추출하고 누락을 방어합니다.

    private String extractEmail(Object principal) {
        String email = null;


        if (principal instanceof Jwt jwt) {
            email = jwt.getClaimAsString("email");
            // (null 혹은 빈 문자열 체크 시 401 에러 반환)
            if (email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰 내 이메일 정보가 유효하지 않습니다.");
            }
        } else if (principal instanceof PrincipalDetails principalDetails) {
            email = principalDetails.getUser().getEmail();
        } else if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        }

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다.");
        }

        return email;
    }




}