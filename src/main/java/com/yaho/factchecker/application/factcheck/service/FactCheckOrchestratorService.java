package com.yaho.factchecker.application.factcheck.service;

import com.yaho.factchecker.application.ai.port.ClaimAnalysisPort;
import com.yaho.factchecker.application.factcheck.dto.request.FactCheckStartRequest;
import com.yaho.factchecker.application.factcheck.dto.response.FactCheckStartResponse;
import com.yaho.factchecker.domain.ai.dto.common.ExtractedClaim;
import com.yaho.factchecker.domain.ai.dto.request.ClaimAnalysisRequest;
import com.yaho.factchecker.domain.ai.dto.response.ClaimAnalysisResponse;
import com.yaho.factchecker.domain.claim.dto.command.ClaimCategoryCreateCommand;
import com.yaho.factchecker.domain.claim.dto.command.ClaimCreateCommand;
import com.yaho.factchecker.domain.claim.dto.response.ClaimResponse;
import com.yaho.factchecker.domain.claim.service.ClaimService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FactCheckOrchestratorService {

    private final ClaimAnalysisPort claimAnalysisPort;
    private final ClaimService claimService;

    public FactCheckStartResponse start(FactCheckStartRequest request) {
        UUID factCheckId = UUID.randomUUID();

        ClaimAnalysisResponse analysisResponse = claimAnalysisPort.analyze(
                new ClaimAnalysisRequest(request.inputText())
        );

        List<ClaimResponse> claims = analysisResponse.claims().stream()
                .map(claim -> toClaimCreateCommand(factCheckId, analysisResponse, claim))
                .map(claimService::createClaim)
                .toList();

        return FactCheckStartResponse.builder()
                .factCheckId(factCheckId)
                .claims(claims)
                .build();
    }

    private ClaimCreateCommand toClaimCreateCommand(
            UUID factCheckId,
            ClaimAnalysisResponse analysisResponse,
            ExtractedClaim claim
    ) {
        return ClaimCreateCommand.builder()
                .factCheckId(factCheckId)
                .claimAnalysisAiLogId(null)
                .originalText(analysisResponse.originalText())
                .canonicalClaim(claim.canonicalClaim())
                .timeScope(claim.timeScope())
                .verifiable(claim.isVerifiable())
                .unverifiableReason(claim.unverifiableReason())
                .categories(toCategoryCommands(claim))
                .build();
    }

    private List<ClaimCategoryCreateCommand> toCategoryCommands(ExtractedClaim claim) {
        return List.of(
                ClaimCategoryCreateCommand.builder()
                        .category(claim.category())
                        .primaryCategory(true)
                        .build()
        );
    }
}

