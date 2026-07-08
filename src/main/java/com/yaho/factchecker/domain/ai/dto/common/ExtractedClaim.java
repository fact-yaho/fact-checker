package com.yaho.factchecker.domain.ai.dto.common;

import com.yaho.factchecker.global.type.ClaimCategory;
import java.util.List;

public record ExtractedClaim(
        String canonicalClaim,
        boolean isVerifiable,
        String unverifiableReason,
        ClaimCategory category,
        List<CountryInfo> countries,
        TimeScopeInfo timeScope
) {
}
