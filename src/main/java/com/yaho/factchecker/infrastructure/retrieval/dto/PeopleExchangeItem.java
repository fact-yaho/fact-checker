package com.yaho.factchecker.infrastructure.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PeopleExchangeItem(
        @JsonProperty("country_nm") String countryNm,
        @JsonProperty("country_eng_nm") String countryEngNm,
        @JsonProperty("country_iso_alp2") String countryIsoAlp2,
        @JsonProperty("exchange_cn") String exchangeCn,   // 교류 내용(인사/직책)
        @JsonProperty("exchange_ty") String exchangeTy,   // 교류 유형(방한/방문 등)
        @JsonProperty("year") Integer year,
        @JsonProperty("month") Integer month,
        @JsonProperty("day") Integer day
) {}
