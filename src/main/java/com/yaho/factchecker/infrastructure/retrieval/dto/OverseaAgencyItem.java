package com.yaho.factchecker.infrastructure.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OverseaAgencyItem(
        @JsonProperty("country_nm") String countryNm,
        @JsonProperty("country_eng_nm") String countryEngNm,
        @JsonProperty("country_iso_alp2") String countryIsoAlp2,
        @JsonProperty("agency_nm") String agencyNm,           // 기관명
        @JsonProperty("agency_ty_cd_nm") String agencyTyCdNm, // 기관 유형
        @JsonProperty("agency_advnc_cn") String agencyAdvncCn,// 진출 내용
        @JsonProperty("written_year") Integer writtenYear     // 작성 연도
) {}
