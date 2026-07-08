package com.yaho.factchecker.infrastructure.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KorRelationItem(
        @JsonProperty("country_nm") String countryNm,
        @JsonProperty("country_eng_nm") String countryEngNm,
        @JsonProperty("country_iso_alp2") String countryIsoAlp2,
        @JsonProperty("diplomatic_relations") String diplomaticRelations,
        @JsonProperty("mission_status") String missionStatus,
        @JsonProperty("export_amount") String exportAmount,
        @JsonProperty("import_amount") String importAmount,
        @JsonProperty("investment_status") String investmentStatus,
        @JsonProperty("oda_status") String odaStatus,
        @JsonProperty("oks_status") String oksStatus
) {}
