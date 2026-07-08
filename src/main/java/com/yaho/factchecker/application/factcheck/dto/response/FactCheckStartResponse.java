package com.yaho.factchecker.application.factcheck.dto.response;

import com.yaho.factchecker.domain.claim.dto.response.ClaimResponse;
import com.yaho.factchecker.domain.scoring.dto.OverallScoreCalculationResult;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record FactCheckStartResponse(
        UUID factCheckId,
        List<ClaimResponse> claims,
        List<ClaimStanceResponse> stanceResults,
        OverallScoreCalculationResult overallScore
) {
}
