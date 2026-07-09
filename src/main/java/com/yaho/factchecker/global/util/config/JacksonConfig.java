package com.yaho.factchecker.global.util.config; //

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();

        // 🎯 Keycloak의 UserRepresentation 클래스가 파싱될 때 모르는 필드(userProfileMetadata 등)를 무시하도록 강제 매핑
        objectMapper.addMixIn(UserRepresentation.class, UserRepresentationMixin.class);

        return objectMapper;
    }

    // 무시 설정을 담은 믹스인 인터페이스
    @JsonIgnoreProperties(ignoreUnknown = true)
    private interface UserRepresentationMixin {
    }
}