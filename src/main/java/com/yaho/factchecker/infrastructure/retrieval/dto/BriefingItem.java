package com.yaho.factchecker.infrastructure.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 외교부 브리핑(/getBriefings) 응답 item
 * content는 HTML 원문(태그 포함) → 정제 후 저장
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BriefingItem(
        String title,                              // 제목
        String content,                            // 내용 (HTML 원문)
        String creator,                            // 작성자/부서 (없으면 null)
        @JsonProperty("file_url") String fileUrl,  // 첨부 URL
        @JsonProperty("updt_date") String updtDate // 작성일 "yyyy-MM-dd HH:mm:ss.SSS"
) {}
