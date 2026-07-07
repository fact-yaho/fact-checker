package com.yaho.factchecker.infrastructure.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NorthKorRelationItem(
        @JsonProperty("country_nm") String countryNm,
        @JsonProperty("country_eng_nm") String countryEngNm,
        @JsonProperty("country_iso_alp2") String countryIsoAlp2,
        @JsonProperty("diplomatic_relations") String diplomaticRelations,
        // 공관현황(북한 측/상대국 측)
        @JsonProperty("emblgbd_status") String emblgbdStatus
) {}
