package com.yaho.factchecker.domain.ai.dto.common;

import lombok.Builder;

@Builder
public record TimeScopeInfo(
        Integer fromYear,
        Integer toYear
) {
}
