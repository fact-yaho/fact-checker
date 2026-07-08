package com.yaho.factchecker.infrastructure.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 외교부 보도자료(/getPressRls) 응답 item
 * content 는 HTML 원문 → 정제 후 저장 (브리핑과 동일 구조)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PressReleaseItem(
        String title,                              // 제목
        String content,                            // 내용 (HTML 원문)
        String creator,                            // 작성자/부서 (없으면 null)
        @JsonProperty("file_url") String fileUrl,  // 첨부 URL
        @JsonProperty("updt_date") String updtDate // 작성일
) {}
