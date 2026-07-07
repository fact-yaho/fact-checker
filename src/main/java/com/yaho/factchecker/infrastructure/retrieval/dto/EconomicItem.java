package com.yaho.factchecker.infrastructure.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EconomicItem(
        @JsonProperty("country_nm") String countryNm,
        @JsonProperty("country_eng_nm") String countryEngNm,
        @JsonProperty("country_iso_alp2") String countryIsoAlp2,
        @JsonProperty("currency_unit") String currencyUnit,
        @JsonProperty("gdp") String gdp,
        @JsonProperty("gdp_growth_rate") String gdpGrowthRate,
        @JsonProperty("gdp_per_capita") String gdpPerCapita,
        @JsonProperty("export_amount") String exportAmount,
        @JsonProperty("import_amount") String importAmount,
        @JsonProperty("inflation_rate") String inflationRate,
        @JsonProperty("unemployment_rate") String unemploymentRate,
        @JsonProperty("main_resource") String mainResource,
        @JsonProperty("major_industry") String majorIndustry
) {}
