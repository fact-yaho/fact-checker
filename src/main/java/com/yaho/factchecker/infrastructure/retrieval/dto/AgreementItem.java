package com.yaho.factchecker.infrastructure.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgreementItem(
        @JsonProperty("country_nm") String countryNm,
        @JsonProperty("country_eng_nm") String countryEngNm,
        @JsonProperty("country_iso_alp2") String countryIsoAlp2,
        // 협정 내용
        @JsonProperty("agreement_cn") String agreementCn,
        @JsonProperty("year") Integer year,
        @JsonProperty("month") Integer month,
        @JsonProperty("day") Integer day
) {}
