package com.yaho.factchecker.infrastructure.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 외교부 연설문(/getSpeeches) 응답 item
 * content는 HTML 원문(태그 포함) → 정제 후 저장, creator 대신 speecher로 필드명 사용
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpeechItem(
        String title,                              // 제목
        String content,                            // 내용 (HTML 원문)
        String speecher,                           // 연설자 (예: "장관")
        @JsonProperty("file_url") String fileUrl,  // 첨부 URL
        @JsonProperty("updt_date") String updtDate // 작성일 "yyyy-MM-dd"
) {}
