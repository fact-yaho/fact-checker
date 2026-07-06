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
        String timeScope,
        boolean verifiable,
        String unverifiableReason,
        List<ClaimCategoryCreateCommand> categories
) {
}
