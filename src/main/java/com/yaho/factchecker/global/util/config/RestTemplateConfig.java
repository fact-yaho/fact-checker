package com.yaho.factchecker.global.util.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 🎯 [분리 이유] RestTemplate 빈이 SecurityConfig 안에 있으면,
 * SecurityConfig -> CustomOAuth2UserService -> UserService -> RestTemplate(SecurityConfig 소속)
 * 순으로 순환 참조가 발생합니다. (UserService가 RestTemplate을 받으려면
 * SecurityConfig 전체가 먼저 생성되어야 하는데, SecurityConfig는
 * CustomOAuth2UserService를, CustomOAuth2UserService는 UserService를 필요로 하기 때문)
 *
 * RestTemplate을 보안 설정과 무관한 별도 Configuration으로 분리해서 이 사이클을 끊습니다.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());

        RestTemplate restTemplate = new RestTemplate(factory);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        restTemplate.getMessageConverters().add(0, converter);
        return restTemplate;
    }
}