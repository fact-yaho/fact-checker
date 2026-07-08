package com.yaho.factchecker.domain.claim.dto.response;

import lombok.Builder;

@Builder
public record TimeScopeResponse(
        Integer fromYear,
        Integer toYear
) {
}
