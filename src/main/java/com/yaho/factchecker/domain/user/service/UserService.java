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
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
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

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;
    private final Keycloak keycloakAdminClient;

    @Value("${keycloak.auth-server-url}")
    private String keycloakAuthServerUrl;

    @Value("${keycloak.client-id}")
    private String keycloakClientId;
    @Value("${keycloak.realm:factchecker}")
    private String realm;

    /* 1. 회원가입 로직 */
    @Transactional
    public UUID signUp(SignUpRequest request) {

        // 새로운 유저 엔티티 생성 기본값 유저
        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()), // 비밀번호 암호화
                request.getName(),
                Role.USER,
                request.getNickname()
        );
        // 변수선언
        User savedUser;

        // 로컬 db 저장
        try {
            savedUser = userRepository.save(user);
            log.info("✅ 로컬 DB 유저 회원가입 성공: {}", request.getEmail());
        } catch (DataIntegrityViolationException e) {
            log.error("❌ 로컬 DB 저장 실패 - 중복된 이메일: {}", request.getEmail());
            throw new IllegalArgumentException("Email already exists : 이미 존재하는 이메일입니다 " + request.getEmail());
        }

        //  [Keycloak 유저 동기화 생성]
        try {
            log.info("🔄 Keycloak 유저 생성 시작: {}", request.getEmail());
            
            // 1) Keycloak 유저 기본 프로필 정의
            UserRepresentation keycloakUser = new UserRepresentation();
            keycloakUser.setUsername(request.getEmail()); // 로그인 ID로 이메일 사용
            keycloakUser.setEmail(request.getEmail());
            keycloakUser.setFirstName(request.getName());
            keycloakUser.setEnabled(true);
            keycloakUser.setEmailVerified(true);

            // 2) Keycloak 내부 로그인 패스워드 설정
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(request.getPassword());
            credential.setTemporary(false);
            keycloakUser.setCredentials(Collections.singletonList(credential));

            // 3) Keycloak Admin API 호출하여 유저 생성
            Response response = null;
            try {
                log.info("📡 Keycloak Admin API 호출 - Realm: {}, Email: {}", realm, request.getEmail());
                response = keycloakAdminClient.realm(realm).users().create(keycloakUser);
                
                int statusCode = response.getStatus();
                log.info("📊 Keycloak 응답 상태 코드: {}", statusCode);
                
                if (statusCode == 201) {
                    log.info("✅ Keycloak 서버 유저 동기화 성공: {}", request.getEmail());
                } else {
                    String errorBody = response.readEntity(String.class);
                    log.error("❌ Keycloak 유저 생성 실패! 상태 코드: {}", statusCode);
                    log.error("❌ Keycloak 서버 에러 응답: {}", errorBody);
                    
                    // 로컬 DB에서 롤백
                    userRepository.delete(savedUser);
                    throw new RuntimeException("Keycloak 사용자 생성 실패 (상태코드: " + statusCode + "): " + errorBody);
                }
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        } catch (Exception e) {
            log.error("❌ Keycloak 동기화 중 예외 발생: {}", e.getMessage(), e);
            // Keycloak 동기화 실패 시 로컬 DB에서 롤백 처리
            try {
                userRepository.delete(savedUser);
                log.info("🔄 로컬 DB 롤백 완료: {}", request.getEmail());
            } catch (Exception rollbackError) {
                log.error("❌ 롤백 실패: {}", rollbackError.getMessage());
            }
            throw new RuntimeException("Keycloak 서버와의 통신에 실패했습니다. 자세한 사유: " + e.getMessage(), e);
        }
        
        log.info("✅ 회원가입 완료: {}", request.getEmail());
        return savedUser.getId();
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
            //
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    keycloakTokenUrl,
                    org.springframework.http.HttpMethod.POST,
                    httpEntity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

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
    public MyPageResponse getMyPage(UUID userId) {
        // 유저가 진짜 있는지 조회하고 가져오기
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. ID: " + userId));

        // 엔티티를  반환
        return new MyPageResponse(user);
    }
}