package com.yaho.factchecker.application.factcheck.service;

import com.yaho.factchecker.application.ai.port.ClaimAnalysisPort;
import com.yaho.factchecker.application.ai.port.StanceAnalysisPort;
import com.yaho.factchecker.application.factcheck.dto.internal.ClaimAnalysisBundle;
import com.yaho.factchecker.application.factcheck.dto.request.FactCheckStartRequest;
import com.yaho.factchecker.application.factcheck.dto.response.ClaimStanceResponse;
import com.yaho.factchecker.application.factcheck.dto.response.FactCheckStartResponse;
import com.yaho.factchecker.domain.ai.dto.common.CountryInfo;
import com.yaho.factchecker.domain.ai.dto.common.ExtractedClaim;
import com.yaho.factchecker.domain.ai.dto.request.ClaimAnalysisRequest;
import com.yaho.factchecker.domain.ai.dto.request.EvidenceForStanceRequest;
import com.yaho.factchecker.domain.ai.dto.request.StanceAnalysisRequest;
import com.yaho.factchecker.domain.ai.dto.response.ClaimAnalysisResponse;
import com.yaho.factchecker.domain.ai.dto.response.StanceAnalysisResponse;
import com.yaho.factchecker.domain.claim.dto.command.ClaimCategoryCreateCommand;
import com.yaho.factchecker.domain.claim.dto.command.ClaimCountryCreateCommand;
import com.yaho.factchecker.domain.claim.dto.command.ClaimCreateCommand;
import com.yaho.factchecker.domain.claim.dto.response.ClaimCategoryResponse;
import com.yaho.factchecker.domain.claim.dto.response.ClaimResponse;
import com.yaho.factchecker.domain.claim.service.ClaimService;
import com.yaho.factchecker.domain.retrieval.dto.ClaimRetrievalRequest;
import com.yaho.factchecker.domain.retrieval.dto.RetrievedEvidence;
import com.yaho.factchecker.domain.retrieval.service.RetrievalService;
import com.yaho.factchecker.global.type.ClaimCategory;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FactCheckOrchestratorService {

    private final ClaimAnalysisPort claimAnalysisPort;
    private final ClaimService claimService;
    private final RetrievalService retrievalService;
    private final StanceAnalysisPort stanceAnalysisPort;

    public FactCheckStartResponse start(FactCheckStartRequest request) {
        UUID factCheckId = UUID.randomUUID();

        ClaimAnalysisResponse analysisResponse = claimAnalysisPort.analyze(
                new ClaimAnalysisRequest(request.inputText())
        );

        List<ClaimResponse> claims = analysisResponse.claims().stream()
                .map(claim -> toClaimCreateCommand(factCheckId, analysisResponse, claim))
                .map(claimService::createClaim)
                .toList();

        List<ClaimAnalysisBundle> analysisBundles = claims.stream()
                .filter(ClaimResponse::verifiable)
                .map(this::analyzeClaim)
                .toList();

        List<ClaimStanceResponse> stanceResults = toClaimStanceResponses(analysisBundles);

        return FactCheckStartResponse.builder()
                .factCheckId(factCheckId)
                .claims(claims)
                .stanceResults(stanceResults)
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
                .fromYear(claim.timeScope() == null ? null : claim.timeScope().fromYear())
                .toYear(claim.timeScope() == null ? null : claim.timeScope().toYear())
                .verifiable(claim.isVerifiable())
                .unverifiableReason(claim.unverifiableReason())
                .categories(toCategoryCommands(claim))
                .countries(toCountryCommands(claim.countries()))
                .build();
    }

    private List<ClaimCategoryCreateCommand> toCategoryCommands(ExtractedClaim claim) {
        if (!claim.isVerifiable() || claim.category() == null) {
            return List.of();
        }

        return List.of(
                ClaimCategoryCreateCommand.builder()
                        .category(claim.category())
                        .primaryCategory(true)
                        .build()
        );
    }

    private List<ClaimCountryCreateCommand> toCountryCommands(List<CountryInfo> countries) {
        if (countries == null) {
            return List.of();
        }

        return countries.stream()
                .map(country -> ClaimCountryCreateCommand.builder()
                        .name(country.name())
                        .code(country.code())
                        .build())
                .toList();
    }

    private ClaimAnalysisBundle analyzeClaim(ClaimResponse claim) {
        ClaimRetrievalRequest retrievalRequest = toRetrievalRequest(claim);

        List<RetrievedEvidence> retrievedEvidences =
                retrievalService.retrieveEvidence(retrievalRequest);

        StanceAnalysisResponse stanceAnalysis = stanceAnalysisPort.analyze(
                new StanceAnalysisRequest(
                        claim.canonicalClaim(),
                        toEvidenceForStanceRequests(retrievedEvidences)
                )
        );

        return ClaimAnalysisBundle.builder()
                .claim(claim)
                .retrievedEvidences(retrievedEvidences)
                .stanceAnalysis(stanceAnalysis)
                .build();
    }

    private List<ClaimStanceResponse> toClaimStanceResponses(
            List<ClaimAnalysisBundle> analysisBundles
    ) {
        return analysisBundles.stream()
                .map(bundle -> ClaimStanceResponse.builder()
                        .claimId(bundle.claim().claimId())
                        .canonicalClaim(bundle.claim().canonicalClaim())
                        .stanceAnalysis(bundle.stanceAnalysis())
                        .build())
                .toList();
    }

    private ClaimRetrievalRequest toRetrievalRequest(ClaimResponse claim) {
        return new ClaimRetrievalRequest(
                claim.claimId(),
                claim.canonicalClaim(),
                claim.verifiable(),
                claim.unverifiableReason(),
                primaryCategory(claim),
                toRetrievalCountries(claim),
                claim.timeScope() == null ? null : claim.timeScope().fromYear(),
                claim.timeScope() == null ? null : claim.timeScope().toYear()
        );
    }

    private ClaimCategory primaryCategory(ClaimResponse claim) {
        return claim.categories().stream()
                .filter(ClaimCategoryResponse::primaryCategory)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("대표 카테고리가 없습니다."))
                .categoryName();
    }

    private List<ClaimRetrievalRequest.CountryInfo> toRetrievalCountries(ClaimResponse claim) {
        if (claim.countries() == null) {
            return List.of();
        }

        return claim.countries().stream()
                .map(country -> new ClaimRetrievalRequest.CountryInfo(
                        country.name(),
                        country.code()
                ))
                .toList();
    }

    private List<EvidenceForStanceRequest> toEvidenceForStanceRequests(
            List<RetrievedEvidence> evidences
    ) {
        return evidences.stream()
                .map(evidence -> new EvidenceForStanceRequest(
                        evidence.evidenceDocumentId(),
                        evidence.title(),
                        evidence.contentCleaned(),
                        evidence.publishedAt() == null ? null : evidence.publishedAt().toLocalDate()
                ))
                .toList();
    }
}

