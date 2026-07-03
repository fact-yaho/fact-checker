package com.yaho.factchecker.domain.user.controller;

import com.yaho.factchecker.domain.user.dto.request.SignUpRequest;
import com.yaho.factchecker.domain.user.dto.response.MyPageResponse;
import com.yaho.factchecker.domain.user.entity.User;
import com.yaho.factchecker.domain.user.repository.UserRepository;
import com.yaho.factchecker.domain.user.service.UserService;
import com.yaho.factchecker.global.util.config.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    // 1. 회원가입 API
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignUpRequest request) {
        Long userId = userService.signUp(request);
        return ResponseEntity.ok("회원가입 완료. 유저 ID: " + userId);
    }

    // 2. 회원탈퇴 API
    @DeleteMapping
    public ResponseEntity<String> deleteUser(@AuthenticationPrincipal UserDetails userDetails) {
        String currentUserEmail = userDetails.getUsername();
        userService.deleteUser(currentUserEmail);
        return ResponseEntity.ok("회원탈퇴 완료. 유저 ID: " + currentUserEmail);
    }

    // 3. 이메일 중복체크 API
    @PostMapping("/check-email")
    public ResponseEntity<String> checkEmail(@RequestParam(name = "email") String email) {
        String cleanEmail = email.replace("\"", "").trim();
        boolean isDuplicate = userService.checkEmailDuplicate(cleanEmail);
        if (isDuplicate) {
            return ResponseEntity.badRequest().body("이미 존재하는 이메일입니다.");
        }
        return ResponseEntity.ok("사용 가능한 이메일입니다.");
    }

    // 4. 닉네임 중복체크 API
    @PostMapping("/check-nickname")
    public ResponseEntity<String> checkNickname(@RequestParam(name = "nickname") String nickname) {
        String cleanNickname = nickname.replace("\"", "").trim();
        boolean isDuplicate = userService.checkNicknameDuplicate(cleanNickname);
        if (isDuplicate) {
            return ResponseEntity.badRequest().body("이미 존재하는 닉네임입니다.");
        }
        return ResponseEntity.ok("사용 가능한 닉네임입니다!.");
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
}