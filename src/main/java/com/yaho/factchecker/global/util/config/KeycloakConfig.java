package com.yaho.factchecker.global.util.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider; // RESTEasy 제공자 임포트
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        // 1) 모르는 필드가 있어도 예외를 던지지 않고 무시하도록 ObjectMapper 설정
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 2) Keycloak 내부 통신 라이브러리(Resteasy)에 커스텀 ObjectMapper 주입
        ResteasyJackson2Provider jacksonProvider = new ResteasyJackson2Provider();
        jacksonProvider.setMapper(mapper);

        // 3) 빌더에 주입하여 클라이언트 생성
        return KeycloakBuilder.builder()
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
    }
}