package com.yaho.factchecker.domain.user.service;

import com.yaho.factchecker.domain.user.entity.Role;
import com.yaho.factchecker.domain.user.entity.User;
import com.yaho.factchecker.domain.user.repository.UserRepository;
import com.yaho.factchecker.domain.user.service.oauth.GoogleUserInfo;
import com.yaho.factchecker.domain.user.service.oauth.KakaoUserInfo;
import com.yaho.factchecker.domain.user.service.oauth.OAuth2UserInfo;
import com.yaho.factchecker.global.util.config.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService <OAuth2UserRequest, OAuth2User>{

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService; // Keycloak 동기화용

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest)  throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String userName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        Map<String,Object> attributes = oAuth2User.getAttributes();

        log.info("CustomOAuth2UserService 로드 완료 - 채널: {}", registrationId);


        OAuth2UserInfo oAuth2UserInfo = null;
        if("google".equals(registrationId)){
            oAuth2UserInfo = new GoogleUserInfo(attributes);
        } else if ("kakao".equals(registrationId)){
            oAuth2UserInfo = new KakaoUserInfo(attributes);
        }else {
            throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인 채널입니다: " + registrationId);
        }


        String email = oAuth2UserInfo.getEmail();
        String name =  oAuth2UserInfo.getName();

        if ("kakao".equals(registrationId) && (email == null || email.isBlank())) {
            // oAuth2User.getAttributes().get("id")는 카카오가 주는 절대 겹치지 않는 숫자 고유값입니다.
            String kakaoId = attributes.get("id").toString();
            email = kakaoId + "@kakao.user"; // 예: 3214342251@kakao.user
        }

        if(email == null||email.isBlank()){
            throw new OAuth2AuthenticationException("이메일 정보를 가져올 수 없습니다. 이메일 제공 동의가 필요합니다.");
        }

        User user = saveOrUpdate(email, name);

        return new PrincipalDetails(user, attributes);
    }

    private User saveOrUpdate(String email, String name){

        return userRepository.findByEmail(email)
                .map(entity -> {
                    // 🎯 [수정] .peek 대신 .map 내부에서 기존 유저 로그를 남깁니다.
                    log.info("기존 사용자 로그인 성공: {}", email);
                    return entity;
                })
                .orElseGet(()->{
                    try {
                        log.info("새로운 OAuth 사용자 생성 시작: email={}, name={}", email, name);

                        String shortUuid = UUID.randomUUID().toString().substring(0, 8); // 8자리 UUID 생성
                        // 이름 누락시 방어 코드
                        String safeName = (name != null && !name.isBlank()) ? name : "user";

                        String uniqueNickname = safeName + "_" + shortUuid; // 닉네임에 UUID를 붙여서 고유하게 만듦

                        //Oauth 사용자를 위한 임시 랜덤 비밀번호
                        String randomRawPassword = UUID.randomUUID().toString();
                        String encodedPassword = passwordEncoder.encode(randomRawPassword);

                        User newUser = User.builder()
                                .email(email)
                                .name(safeName)
                                .nickname(uniqueNickname)
                                .role(Role.USER)
                                .password(encodedPassword)
                                .build();

                        User savedUser = userRepository.save(newUser);
                        log.info("새로운 OAuth 사용자 생성 완료: email={}, nickname={}", email, uniqueNickname);

                        // 🎯 [추가] Keycloak에도 동일한 유저를 동기화 생성합니다.
                        //     이게 없으면 이후 generateTokenForOAuth()에서 Keycloak에 유저를 못 찾아
                        //     비밀번호 리셋이 조용히 스킵되고, 토큰 발급 시 401이 발생합니다.
                        try {
                            userService.createKeycloakUserIfNotExists(email, safeName, randomRawPassword);
                        } catch (Exception e) {
                            // Keycloak 동기화가 실패해도 로컬 로그인 자체는 막지 않습니다.
                            // (generateTokenForOAuth 호출 시 재시도되도록 다음 로그인에서 다시 확인됩니다.)
                            log.error("❌ OAuth 신규 유저 Keycloak 동기화 실패: email={}", email, e);
                        }

                        return savedUser;
                    } catch (Exception e) {
                        log.error("OAuth 사용자 생성 중 오류 발생: email={}", email, e);
                        // 🎯 OAuth2AuthenticationException 생성자에 맞게 예외 처리 변경
                        throw new OAuth2AuthenticationException("사용자 등록 중 오류가 발생했습니다: " + e.getMessage());
                    }
                });
    }




}