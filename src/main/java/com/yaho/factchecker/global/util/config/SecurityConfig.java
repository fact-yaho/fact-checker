package com.yaho.factchecker.global.util.config;

import com.yaho.factchecker.domain.user.service.CustomOAuth2UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    // 순환 참조 방지를 위해 ApplicationContext를 동적으로 활용합니다.
    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers(
                        "/favicon.ico", "/favicon.png", "/error",
                        "/css/**", "/js/**", "/images/**"
                );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 🔧 변경: JWT 스테이트리스 REST라 /api/** 전체를 CSRF 예외로.
                //         (login·send-verification·verify-code·DELETE users가 빠져 있던 문제 해결)
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/api/**",
                                "/oauth2/**",
                                "/login/oauth2/**"
                        )
                )
                .cors(cors -> cors.disable())

                // 세션정책
                .sessionManagement(session -> session
                        .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED)
                )
                // URL별 권한 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                // ── 뷰 페이지 (껍데기 public, 데이터는 JS가 Bearer로 fetch) ──
                                "/", "/login", "/signup", "/mypage",
                                "/result", "/history",
                                // ── 인증 없이 호출되는 API ──
                                "/api/v1/auth/login",
                                "/api/v1/users/signup",
                                "/api/v1/users/check-email",
                                "/api/v1/users/check-nickname",
                                "/api/v1/users/send-verification",
                                "/api/v1/users/verify-code",
                                "/api/v1/fact-checks",
                                "/api/v1/users/reset-password",
                                "/reset-password"
                        ).permitAll()
                        // 나머지(=results/histories/**, users/me, DELETE users 등)는 로그인 필요
                        .anyRequest().authenticated()
                )
                // 일반 폼 로그인 설정
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                )
                // OAuth2 로그인 설정
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler((request, response, authentication) -> {
                            org.springframework.security.oauth2.core.user.OAuth2User oAuth2User =
                                    (org.springframework.security.oauth2.core.user.OAuth2User) authentication.getPrincipal();

                            String email = (String) oAuth2User.getAttributes().get("email");
                            if (email == null || email.isBlank()) {
                                email = (String) oAuth2User.getAttributes().get("preferred_username");
                            }

                            log.info("🎯 OAuth2 소셜 로그인 성공! 추출된 이메일: {}", email);

                            // 🎯 런타임 시점에 ApplicationContext에서 UserService 빈을 직접 꺼내와 심볼 에러와 순환 참조를 동시에 해결합니다.
                            com.yaho.factchecker.domain.user.service.UserService currentUserService =
                                    applicationContext.getBean(com.yaho.factchecker.domain.user.service.UserService.class);

                            // 토큰 발급 메서드 호출
                            String realToken = currentUserService.generateTokenForOAuth(email);

                            log.info("🚀 실제 소셜 토큰 생성 완료. 마이페이지로 파라미터를 실어 리다이렉트합니다.");

                            // 쿼리 스트링으로 발급된 진짜 JWT 토큰을 주소창에 매핑해서 전송
                            response.sendRedirect("/mypage?token=" + realToken);
                        })
                )
                // jwt 리소스 서버 설정
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                );

        return http.build();
    }
}
