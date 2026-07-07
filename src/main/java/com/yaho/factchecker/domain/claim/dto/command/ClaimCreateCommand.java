package com.yaho.factchecker.domain.claim.dto.command;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ClaimCreateCommand(
        UUID factCheckId,
        UUID claimAnalysisAiLogId,
        String originalText,
        String canonicalClaim,
        Integer fromYear,
        Integer toYear,
        boolean verifiable,
        String unverifiableReason,
        List<ClaimCategoryCreateCommand> categories,
        List<ClaimCountryCreateCommand> countries
) {
}
