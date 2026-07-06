package com.yaho.factchecker.domain.claim.dto.response;

import java.util.List;
import java.util.UUID;

public record ClaimResponse(
        UUID claimId,
        UUID factCheckId,
        UUID claimAnalysisAiLogId,
        String originalText,
        String canonicalClaim,
        String timeScope,
        boolean verifiable,
        String unverifiableReason,
        List<ClaimCategoryResponse> categories,
        List<ClaimCountryResponse> countries
) {
}
