package com.yaho.factchecker.infrastructure.retrieval.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Logger;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * < MOFA Feign 전용 >
 * item이 1건일 때 배열이 아닌 객체로 오는 경우 대응(ACCEPT_SINGLE_VALUE_AS_ARRAY)
 * 매핑 안 된 응답 필드 무시(FAIL_ON_UNKNOWN_PROPERTIES=false)
 * Logger.Level.BASIC = 실제 요청 URL/상태 확인용 (cond 인코딩 검증에 필요)
 */
@Configuration
public class MofaFeignConfig {

    @Bean
    public Decoder mofaFeignDecoder() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new JacksonDecoder(mapper);
    }

    @Bean
    public Logger.Level mofaFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
