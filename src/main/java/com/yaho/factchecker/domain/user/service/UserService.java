package com.yaho.factchecker.domain.user.service;

import com.yaho.factchecker.domain.user.dto.request.LoginRequest;
import com.yaho.factchecker.domain.user.dto.request.SignUpRequest;
import com.yaho.factchecker.domain.user.dto.response.LoginResponse;
import com.yaho.factchecker.domain.user.dto.response.MyPageResponse;
import com.yaho.factchecker.domain.user.entity.Role;
import com.yaho.factchecker.domain.user.entity.User;
import com.yaho.factchecker.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;

    @Value("${keycloak.auth-server-url}")
    private String keycloakAuthServerUrl;

    @Value("${keycloak.client-id}")
    private String keycloakClientId;

    /* 1. 회원가입 로직 */
    @Transactional
    public Long signUp(SignUpRequest request) {

        // 새로운 유저 엔티티 생성 기본값 유저
        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()), // 비밀번호 암호화
                request.getName(),
                Role.USER,
                request.getNickname()

        );
        try {
            User savedUser = userRepository.save(user);
            return savedUser.getId();
        }catch (DataIntegrityViolationException e){
            throw new IllegalArgumentException("Email already exists : 이미 존재하는 이메일입니다"+request.getEmail());
        }

    }

    /* 2. 회원 삭제 로직 */
    @Transactional
    public void deleteUser(String email) {
        // 1. 이메일로 유저가 존재하는지 먼저 확인 겸 엔티티 가져오기
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. 이메일: " + email));

        // 2. 찾아온 유저의 진짜 고유 ID로 확실하게 삭제 진행
        userRepository.deleteById(user.getId());
    }

    /* 3. 이메일 중복 체크 */
    public boolean checkEmailDuplicate(String email) {
        return userRepository.existsByEmail(email);
    }

    /* 4. 닉네임 중복 체크 */
    public boolean checkNicknameDuplicate(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    /* 5. 로그인 로직 */
    public  LoginResponse login(LoginRequest request) {

        // 이메일로 유저 조회
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: 이메일을 찾을수 없습니다 " + request.getEmail()));

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid password: 비밀번호가 일치하지 않습니다");
        }
    //  주입받은 환경변수를 활용하여 엔드포인트 설정
        String keycloakTokenUrl = keycloakAuthServerUrl + "/realms/factchecker/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", "factchecker-client");
        formData.add("username", request.getEmail());
        formData.add("password", request.getPassword());

        HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(formData, headers);

        try {
            // Keycloak 서버로 요청을 보내 토큰 응답 받기
            ResponseEntity<Map> response = restTemplate.postForEntity(keycloakAuthServerUrl + "/protocol/openid-connect/token", httpEntity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            //  정상적으로 받아온 토큰 데이터를 Response DTO에 맵핑하여 반환
            return new LoginResponse(
                    (String) responseBody.get("access_token"),
                    (String) responseBody.get("refresh_token"),
                    (String) responseBody.get("token_type"),
                    ((Number) responseBody.get("expires_in")).longValue()
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("인증 서버와의 통신에 실패했거나 계정 정보가 올바르지 않습니다.", e);
        }
    }

    // 6. 마이페이지
    public MyPageResponse getMyPage(Long userId) {
        // 유저가 진짜 있는지 조회하고 가져오기
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. ID: " + userId));

        // 엔티티를  반환
        return new MyPageResponse(user);
    }
}