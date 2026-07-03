package com.yaho.factchecker.global.util.config;

import com.yaho.factchecker.domain.user.service.CustomOAth2UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomOAth2UserService customOAth2UserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/api/v1/users/signup",
                                "/api/v1/users/check-email",
                                "/api/v1/users/check-nickname",
                                "/oauth2/**",
                                "/login/oauth2/**"
                        )
                )
                .cors(cors -> cors.disable())

                // 세션정책
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                .authorizeHttpRequests(auth -> auth

                    //회원 가입전 필수 검증 api 세트와 Oauth api 전체
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/api/v1/users/signup", "/api/v1/users/check-nickname","/api/v1/users/check-email").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .anyRequest().authenticated()
                )
                // OAuth2 로그인 설정 추가
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAth2UserService)
                        )
                        .defaultSuccessUrl("/", true)  // 로그인 성공 후 리다이렉트 URL
                )
                // jwt 리소스 서버 설정
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RestTemplate restTemplate() {
        
        // 요청 팩토리 생성
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 타임아웃 10초
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        // 타임아웃 팩토리 설정 반환
        return new RestTemplate(factory);
    }




}