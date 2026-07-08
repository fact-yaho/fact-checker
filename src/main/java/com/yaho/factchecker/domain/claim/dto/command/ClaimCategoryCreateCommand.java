package com.yaho.factchecker.domain.claim.dto.command;

import com.yaho.factchecker.global.type.ClaimCategory;
import lombok.Builder;

@Builder
public record ClaimCategoryCreateCommand(
        ClaimCategory category,
        boolean primaryCategory
) {
}
