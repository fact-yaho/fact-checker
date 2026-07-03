package com.yaho.factchecker.infrastructure.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmbassyItem(
        @JsonProperty("country_nm") String countryNm,
        @JsonProperty("country_eng_nm") String countryEngNm,
        @JsonProperty("country_iso_alp2") String countryIsoAlp2,
        @JsonProperty("embassy_kor_nm") String embassyKorNm,       // 공관명
        @JsonProperty("embassy_ty_cd_nm") String embassyTyCdNm,    // 공관 유형(대사관 등)
        @JsonProperty("emblgbd_addr") String emblgbdAddr,          // 주소
        @JsonProperty("tel_no") String telNo                       // 전화
) {}
