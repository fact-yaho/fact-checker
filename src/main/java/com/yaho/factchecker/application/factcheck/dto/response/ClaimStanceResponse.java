package com.yaho.factchecker.application.factcheck.dto.response;

import com.yaho.factchecker.domain.ai.dto.response.StanceAnalysisResponse;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ClaimStanceResponse(
        UUID claimId,
        String canonicalClaim,
        StanceAnalysisResponse stanceAnalysis
) {
}
