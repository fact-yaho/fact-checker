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

       if(email == null||email.isBlank()){
           throw new OAuth2AuthenticationException("이메일 정보를 가져올 수 없습니다. 이메일 제공 동의가 필요합니다.");
       }

       User user = saveOrUpate(email, name);

        return new PrincipalDetails(user, attributes);
    }

    private User saveOrUpate(String email, String name){

        return userRepository.findByEmail(email)
                .map(entity->entity)
                .orElseGet(()->{

                    String shortUuid = UUID.randomUUID().toString().substring(0, 8); // 8자리 UUID 생성
                    // 이름 누락시 방어 코드
                    String safeName = (name != null && !name.isBlank()) ? name : "user";

                    String uniqueNickname = name + "_" + shortUuid; // 닉네임에 UUID를 붙여서 고유하게 만듦

                    User newUser =User.builder()
                            .email(email)
                            .name(name)
                            .nickname(uniqueNickname) // 구글 이름을 기본 닉네임으로 설정 예시
                            .role(Role.USER) // 기본 역할 설정
                            .build();
                    return userRepository.save(newUser);
                });
    }




}
