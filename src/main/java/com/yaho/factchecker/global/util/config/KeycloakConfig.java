package com.yaho.factchecker.global.util.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class KeycloakConfig {

    @Value("${keycloak.auth-server-url:http://localhost:8081}")
    private String serverUrl;

    @Value("${keycloak.admin.username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin.password:admin}")
    private String adminPassword;

    @Bean
    public Keycloak keycloakAdminClient() {
        log.info("🔧 Keycloak Admin Client 초기화 시작...");
        log.info("   서버 URL: {}", serverUrl);
        log.info("   관리자 계정: {}", adminUsername);
        
        try {
            // 1) 모르는 필드가 있어도 예외를 던지지 않고 무시하도록 ObjectMapper 설정
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            log.info("   ✓ ObjectMapper 설정 완료 (unknown fields 무시)");

            // 2) Keycloak 내부 통신 라이브러리(Resteasy)에 커스텀 ObjectMapper 주입
            ResteasyJackson2Provider jacksonProvider = new ResteasyJackson2Provider();
            jacksonProvider.setMapper(mapper);
            log.info("   ✓ ResteasyJackson2Provider 설정 완료");

            // 3) 빌더에 주입하여 클라이언트 생성
            Keycloak keycloak = KeycloakBuilder.builder()
                    .serverUrl(serverUrl)
                    .realm("master")
                    .clientId("admin-cli")
                    .username(adminUsername)
                    .password(adminPassword)
                    .resteasyClient(
                            new org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl()
                                    .register(jacksonProvider)
                                    .build()
                    )
                    .build();
            
            log.info("✅ Keycloak Admin Client 초기화 성공!");
            return keycloak;
        } catch (Exception e) {
            log.error("❌ Keycloak Admin Client 초기화 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Keycloak 클라이언트 초기화 실패", e);
        }
    }
}