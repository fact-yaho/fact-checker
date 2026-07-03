package com.yaho.factchecker.domain.user.service;

import com.yaho.factchecker.domain.user.entity.Role;
import com.yaho.factchecker.domain.user.entity.User;
import com.yaho.factchecker.domain.user.repository.UserRepository;
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
public class CustomOAth2UserService implements OAuth2UserService <OAuth2UserRequest, OAuth2User>{

    private final UserRepository userRepository;
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest)  throws OAuth2AuthenticationException {

       OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
       OAuth2User oAuth2User = delegate.loadUser(userRequest);

       String registrationId = userRequest.getClientRegistration().getRegistrationId();
       String userName = userRequest.getClientRegistration()
               .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

       Map<String,Object> attributes = oAuth2User.getAttributes();

       String email = (String) attributes.get("email");
       String name = (String) attributes.get("name");


       log.info("CustomOAth2UserService loadUser 호출");

       User user = saveOrUpate(email, name);


       //PrincipalDetails 객체에 엔티티와 속성값을 묶어서 반환
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
