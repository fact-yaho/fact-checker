package com.yaho.factchecker.domain.claim.dto.command;

import com.yaho.factchecker.global.type.ClaimCategory;

public record ClaimCategoryCreateCommand(
        ClaimCategory category,
        boolean primaryCategory
) {
}
