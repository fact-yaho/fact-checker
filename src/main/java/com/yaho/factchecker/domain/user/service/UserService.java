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
import org.springframework.web.client.HttpClientErrorException;   // 🆕 [보안] Keycloak 4xx(자격증명 거부) 구분용
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
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
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

                } else if (statusCode == 409) {
                    // 🆕 [자가 복구] 재가입 케이스.
                    //     탈퇴 시 Keycloak 유저가 남아 있으면 409("User exists with same username/email")가 납니다.
                    //     바로 위 로컬 저장이 unique 제약을 통과했다는 것은
                    //     "로컬에는 이 이메일이 없다" = Keycloak 잔존분은 100% 유령 유저라는 뜻이므로
                    //     안전하게 삭제 후 재생성합니다.
                    log.warn("⚠️ Keycloak 409 감지 → 유령 유저 정리 후 재생성: {}", request.getEmail());

                    keycloakAdminClient.realm(realm).users().search(request.getEmail()).stream()
                            .filter(u -> request.getEmail().equalsIgnoreCase(u.getEmail())
                                      || request.getEmail().equalsIgnoreCase(u.getUsername()))
                            .findFirst()
                            .ifPresent(u -> keycloakAdminClient.realm(realm).users().get(u.getId()).remove());

                    try (Response retry = keycloakAdminClient.realm(realm).users().create(keycloakUser)) {
                        if (retry.getStatus() == 201) {
                            log.info("✅ 유령 유저 정리 후 Keycloak 재생성 성공: {}", request.getEmail());
                        } else {
                            String retryErrorBody = retry.readEntity(String.class);
                            log.error("❌ Keycloak 재생성 실패! 상태 코드: {} / 응답: {}", retry.getStatus(), retryErrorBody);
                            userRepository.delete(savedUser); // 로컬 DB 롤백
                            throw new RuntimeException("Keycloak 재생성 실패 (상태코드: " + retry.getStatus() + "): " + retryErrorBody);
                        }
                    }

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
            // ⚠️ 이 상세 메시지는 "로그 전용"입니다.
            //    UserController 가 사용자에게는 일반 문구만 내려주므로 내부 정보가 노출되지 않습니다.
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

        // 2. 🆕 [중요] Keycloak 유저를 먼저 삭제합니다.
        //    이걸 안 하면 탈퇴 후 재가입 시 Keycloak에 유령 유저가 남아
        //    409 "User exists with same username/email" 로 가입이 막힙니다.
        //    (Keycloak username 을 이메일로 세팅하고 있으므로 email/username 정확 일치분만 삭제)
        //    여기서 실패하면 예외를 던져 로컬 삭제까지 롤백 → 정합성 유지.
        try {
            keycloakAdminClient.realm(realm).users().search(email).stream()
                    .filter(u -> email.equalsIgnoreCase(u.getEmail())
                              || email.equalsIgnoreCase(u.getUsername()))
                    .findFirst()
                    .ifPresentOrElse(u -> {
                        keycloakAdminClient.realm(realm).users().get(u.getId()).remove();
                        log.info("✅ Keycloak 유저 삭제 완료: {}", email);
                    }, () -> log.warn("⚠️ Keycloak에 삭제할 유저가 없습니다(이미 없음): {}", email));
        } catch (Exception e) {
            log.error("❌ Keycloak 유저 삭제 실패: {}", e.getMessage(), e);
            throw new IllegalStateException("회원 탈퇴 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", e);
        }

        // 3. 찾아온 유저의 진짜 고유 ID로 로컬 DB 삭제 진행
        userRepository.deleteById(user.getId());
        log.info("✅ 회원 탈퇴 완료(로컬 + Keycloak): {}", email);
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
    public LoginResponse login(LoginRequest request) {

        // 🔒 [보안 · 계정 열거(user enumeration) 방지]
        //    "이메일이 없음" 과 "비밀번호가 틀림" 의 응답 문구가 다르면,
        //    공격자가 이메일만 바꿔가며 요청해 "어떤 계정이 가입돼 있는지" 를 골라낼 수 있습니다.
        //    → 사용자에게 보이는 문구는 완전히 동일하게 두고, 실제 원인은 로그로만 남깁니다.
        //    (아래 세 곳의 문구가 글자 하나까지 같아야 의미가 있습니다.)
        final String LOGIN_FAIL_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";

        // 이메일로 유저 조회
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("⚠️ 로그인 실패(미존재 이메일): {}", request.getEmail()); // 원인은 로그에만
                    return new IllegalArgumentException(LOGIN_FAIL_MESSAGE);
                });

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("⚠️ 로그인 실패(비밀번호 불일치): {}", request.getEmail()); // 원인은 로그에만
            throw new IllegalArgumentException(LOGIN_FAIL_MESSAGE);
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

        } catch (HttpClientErrorException e) {
            // 🔒 Keycloak 이 자격 증명을 거부(4xx, invalid_grant 등) → 위와 동일한 문구로 통일
            log.warn("⚠️ 로그인 실패(Keycloak 자격 증명 거부): {} / status={}", request.getEmail(), e.getStatusCode());
            throw new IllegalArgumentException(LOGIN_FAIL_MESSAGE, e);

        } catch (Exception e) {
            // ⚠️ Keycloak 다운 · 네트워크 오류 등 → 사용자 잘못이 아니므로 "비밀번호 틀림" 과 반드시 구분.
            //    (예전처럼 뭉뚱그리면 인증 서버가 죽어도 사용자는 비밀번호만 계속 다시 치게 됩니다.)
            //    내부 원인은 로그에만 남깁니다.
            log.error("❌ 인증 서버(Keycloak) 통신 오류: {}", e.getMessage(), e);
            throw new IllegalStateException("일시적인 오류로 로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.", e);
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

    /* 🆕 7. 비밀번호 재설정 (로컬 DB + Keycloak 동시 갱신) */
    //    ⚠️ User 엔티티에 아래 메서드가 있어야 합니다.
    //       public void updatePassword(String encodedPassword) { this.password = encodedPassword; }
    @Transactional
    public void resetPassword(String email, String newRawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        // 1) 로컬 DB 비밀번호 갱신 (BCrypt)
        user.updatePassword(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
        log.info("✅ 로컬 DB 비밀번호 갱신 완료: {}", email);

        // 2) Keycloak 비밀번호 동기화
        //    실제 로그인 토큰은 Keycloak 이 발급하므로, 여기까지 해야 새 비밀번호로 로그인됩니다.
        //    (로컬만 바꾸면 로컬 검증은 통과하는데 Keycloak 단계에서 예전 비밀번호로 막힙니다.)
        try {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newRawPassword);
            credential.setTemporary(false);

            // 예전 가입 계정 등 Keycloak 에 유저가 없을 수도 있으므로 자가 복구
            createKeycloakUserIfNotExists(email, user.getName(), newRawPassword);

            keycloakAdminClient.realm(realm).users().search(email).stream()
                    .filter(u -> email.equalsIgnoreCase(u.getEmail())
                              || email.equalsIgnoreCase(u.getUsername()))
                    .findFirst()
                    .ifPresentOrElse(userRep -> {
                        keycloakAdminClient.realm(realm).users().get(userRep.getId()).resetPassword(credential);
                        log.info("✅ Keycloak 비밀번호 동기화 완료: {}", email);
                    }, () -> log.error("❌ Keycloak 에서 유저를 찾을 수 없습니다: {}", email));

        } catch (Exception e) {
            // 동기화 실패 시 예외를 던져 위의 로컬 갱신까지 롤백 → 로컬/Keycloak 비밀번호 불일치 방지
            log.error("❌ Keycloak 비밀번호 동기화 실패: {}", e.getMessage(), e);
            throw new IllegalStateException("비밀번호 재설정 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", e);
        }
    }

    // 🎯 소셜 로그인 성공 유저를 위한 JWT 토큰 발급 로직
    public String generateTokenForOAuth(String email) {
        // 1. DB에서 해당 이메일을 가진 유저가 있는지 확인합니다.
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다: " + email));

        // 🔒 [보안 수정] 고정된 공용 비밀번호 대신, 호출할 때마다 랜덤 임시 비밀번호를 발급하고
        //     토큰 발급 직후 폐기합니다. 고정값이면 이 문자열을 아는 사람이 누구든
        //     일반 로그인(/api/v1/auth/login)으로 해당 이메일 계정에 침투할 수 있기 때문입니다.
        String oauthTemporaryPassword = UUID.randomUUID().toString() + "!Aa1";

        // 🎯 [자가 복구] 예전에 가입해서 Keycloak 동기화가 안 되어 있던 유저도
        //     여기서 항상 먼저 존재 여부를 확인 후 없으면 생성합니다.
        //     (신규/기존 유저 분기와 무관하게 토큰 발급 시점마다 보장)
        try {
            createKeycloakUserIfNotExists(email, user.getName(), oauthTemporaryPassword);
        } catch (Exception e) {
            log.warn("⚠️ Keycloak 유저 존재 확인/생성 중 경고 발생: {}", e.getMessage());
        }

        try {
            log.info("🔄 소셜 로그인 사용자의 Keycloak 자격 증명 동기화 설정 시작: {}", email);

            // 2. 💡 Keycloak Admin Client를 활용하여, 해당 유저의 Keycloak 비밀번호를 임시 키로 강제 업데이트(원격 제어)합니다.
            org.keycloak.representations.idm.CredentialRepresentation credential = new org.keycloak.representations.idm.CredentialRepresentation();
            credential.setType(org.keycloak.representations.idm.CredentialRepresentation.PASSWORD);
            credential.setValue(oauthTemporaryPassword);
            credential.setTemporary(false);

            // Keycloak 내부의 유저를 찾아 비밀번호를 즉시 변경시킵니다.
            keycloakAdminClient.realm(realm).users().search(email)
                    .stream()
                    .findFirst()
                    .ifPresentOrElse(userRep -> {
                        keycloakAdminClient.realm(realm).users().get(userRep.getId()).resetPassword(credential);
                        log.info("✅ Keycloak 원격 유저 패스워드 동기화 성공 (비밀번호 검증 우회)");
                    }, () -> log.error("❌ Keycloak에서 유저를 여전히 찾을 수 없습니다: {}", email));

        } catch (Exception e) {
            log.warn("⚠️ Keycloak 유저 패스워드 동기화 중 경고 발생 (기존 가입 계정 검증): {}", e.getMessage());
        }

        // 3. 💡 동기화된 비밀번호를 활용해 일반 로그인과 완벽히 동일한 'factchecker-client' 토큰을 정식 요청합니다.
        String url = keycloakAuthServerUrl + "/realms/factchecker/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "password");
        params.add("client_id", "factchecker-client");
        params.add("username", user.getEmail());
        params.add("password", oauthTemporaryPassword); // 동기화시킨 비밀번호 전달

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.POST,
                    requestEntity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null && responseBody.containsKey("access_token")) {
                log.info("🚀 [성공] 소셜 로그인 유저 전용 정식 Access Token 발급 완료");
                return (String) responseBody.get("access_token");
            }
            throw new IllegalArgumentException("Keycloak 토큰 응답에 access_token이 누락되었습니다.");
        } catch (Exception e) {
            log.error("❌ 소셜 로그인용 정식 토큰 발행 최종 실패: {}", e.getMessage(), e);
            throw new IllegalArgumentException("인증 서버로부터 유저 토큰을 발행하는 데 실패했습니다.", e);
        }
    }

    // 키클록 db 에저장
    public void createKeycloakUserIfNotExists(String email, String name, String rawPassword) {
        boolean exists = !keycloakAdminClient.realm(realm).users().search(email).isEmpty();
        if (exists) return;

        UserRepresentation keycloakUser = new UserRepresentation();
        keycloakUser.setUsername(email);
        keycloakUser.setEmail(email);
        keycloakUser.setFirstName(name);
        keycloakUser.setEnabled(true);
        keycloakUser.setEmailVerified(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(rawPassword);
        credential.setTemporary(false);
        keycloakUser.setCredentials(Collections.singletonList(credential));

        try (Response response = keycloakAdminClient.realm(realm).users().create(keycloakUser)) {
            if (response.getStatus() != 201) {
                log.error("❌ OAuth 유저 Keycloak 동기화 실패: {}", response.readEntity(String.class));
            }
        }
    }
}
