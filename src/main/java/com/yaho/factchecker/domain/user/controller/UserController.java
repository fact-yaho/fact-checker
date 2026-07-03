package com.yaho.factchecker.domain.user.controller;


import com.yaho.factchecker.domain.user.dto.request.SignUpRequest;
import com.yaho.factchecker.domain.user.dto.response.MyPageResponse;
import com.yaho.factchecker.domain.user.entity.User;
import com.yaho.factchecker.domain.user.service.UserService;
import com.yaho.factchecker.global.util.config.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor


public class UserController {

    private final UserService userService;

    //회원가입 api
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignUpRequest request){
        Long userId = userService.signUp(request);
        return ResponseEntity.ok("회원가입  완료" + userId);

    }
    // 2. 회원탈퇴 API (Delete)
    @DeleteMapping
    public ResponseEntity<String> deleteUser(@AuthenticationPrincipal UserDetails userDetails) {

        //userDetails.getUsername()을 하면 로그인할 때 사용한 '이메일'
        String currentUserEmail = userDetails.getUsername();

        userService.deleteUser(currentUserEmail);

        return ResponseEntity.ok("회원탈퇴 완료. 유저 ID: " + currentUserEmail);
    }


    // 3.이메일 중복체크
    @PostMapping("/check-email")
    public ResponseEntity<String> checkEmail(@RequestParam(name = "email") String email){

    String cleanEmail =email.replace("\"", "")
            .trim(); // 공백 제거

        boolean isDuplicate = userService.checkEmailDuplicate(cleanEmail);
        if(isDuplicate){
            return ResponseEntity.badRequest().body("이미 존재하는 이메일입니다.");
        }
        return ResponseEntity.ok("사용  가능한 이메일입니다.");
    }
    // 4.닉네임 중복 체크 api
    @PostMapping("/check-nickname")
    public ResponseEntity<String> checkNickname(@RequestParam(name = "nickname") String nickname) {
        String cleanNickname = nickname.replace("\"", "").trim(); // 공백 제거
        boolean isDuplicate = userService.checkNicknameDuplicate(cleanNickname);
        if (isDuplicate) {
            return ResponseEntity.badRequest().body("이미 존재하는 닉네임입니다.");
        }
        return ResponseEntity.ok("사용 가능한 닉네임입니다!.");
    }

    //5. 마이페이지 api 추가
    @PostMapping("/me")

    // principalDetails를 통해 현재 로그인한 사용자의 정보를 가져옵니다.15
    public ResponseEntity<MyPageResponse>getMyPage(@AuthenticationPrincipal PrincipalDetails principalDetails) {
        User user = principalDetails.getUser();

        return ResponseEntity.ok(new MyPageResponse(user));
    }

}
