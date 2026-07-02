package com.yaho.factchecker.domain.claim.dto.response;

import com.yaho.factchecker.global.type.ClaimCategory;
import java.util.UUID;

public record ClaimCategoryResponse(
        UUID categoryId,
        ClaimCategory categoryName,
        boolean primaryCategory
) {
}
