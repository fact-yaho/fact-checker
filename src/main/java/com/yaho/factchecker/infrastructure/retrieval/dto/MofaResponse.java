package com.yaho.factchecker.infrastructure.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 공공데이터포털 표준 응답 DTO. item 타입만 API별로 바꿔 재사용.
 * (ex) MofaResponse<KorRelationItem>
 *
 * 응답 구조: response > (header, body > items > item[])
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MofaResponse<T>(Response<T> response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response<T>(Header header, Body<T> body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body<T>(Items<T> items, Integer numOfRows, Integer pageNo, Integer totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items<T>(List<T> item) {}

    // 결과 리스트 추출 (없으면 빈 리스트)
    public List<T> items() {
        if (response == null || response.body() == null
                || response.body().items() == null || response.body().items().item() == null) {
            return List.of();
        }
        return response.body().items().item();
    }

    // 성공 판별. 외교부 API는 정상 시 resultCode="0". header 없으면 파싱 성공을 성공으로 간주
    public boolean isSuccess() {
        if (response == null) {
            return false;
        }
        if (response.header() == null) {
            return true;
        }
        String code = response.header().resultCode();
        return code == null || "0".equals(code) || "00".equals(code) || "0000".equals(code);
    }
}
