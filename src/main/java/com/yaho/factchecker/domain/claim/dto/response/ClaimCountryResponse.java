package com.yaho.factchecker.domain.claim.dto.response;

import lombok.Builder;

@Builder
public record ClaimCountryResponse(
        String name,
        String code
) {
}
