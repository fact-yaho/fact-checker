package com.yaho.factchecker.infrastructure.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 외교부 외교안보연구소(IFANS) 발간자료(/getIfansPblctList) 응답 item
 * description 이 본문(발간물 요약/내용) → 정제 후 저장
 * cond(author/title)가 있으나 국가 조건이 없어 소주장 조건검색 불가 → A부류(코퍼스)로 적재
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfansPublicationItem(
        String title,                                  // 발간물 제목
        String author,                                 // 저자
        String description,                            // 본문(발간물 요약/내용)
        @JsonProperty("ctgry_nm") String ctgryNm,      // 분류명 (예: Past Publications)
        @JsonProperty("data_url") String dataUrl,      // 원문 URL
        @JsonProperty("idx_no") Integer idxNo,         // 발간물 식별 번호
        @JsonProperty("pub_date") String pubDate       // 발간일 "yyyy-MM-dd"
) {}
