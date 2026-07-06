package com.yaho.factchecker.domain.claim.dto.command;

import lombok.Builder;

@Builder
public record ClaimCountryCreateCommand(
        String name,
        String code
) {
}
