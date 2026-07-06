package com.yaho.factchecker.infrastructure.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TradeItem(
        @JsonProperty("country_nm") String countryNm,
        @JsonProperty("country_eng_nm") String countryEngNm,
        @JsonProperty("country_iso_alp2") String countryIsoAlp2,
        @JsonProperty("export_cn") String exportCn,           // 주요 수출품
        @JsonProperty("income_cn") String incomeCn,           // 주요 수입품
        @JsonProperty("yt_export_amount") Long ytExportAmount,// 수출액
        @JsonProperty("yt_income_amount") Long ytIncomeAmount,// 수입액
        @JsonProperty("yt_trade_year") String ytTradeYear     // 교역 연도
) {}
