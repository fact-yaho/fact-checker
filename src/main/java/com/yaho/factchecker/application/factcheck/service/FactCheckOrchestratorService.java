package com.yaho.factchecker.application.factcheck.service;

import com.yaho.factchecker.application.ai.port.ClaimAnalysisPort;
import com.yaho.factchecker.application.ai.port.StanceAnalysisPort;
import com.yaho.factchecker.application.factcheck.dto.internal.ClaimAnalysisBundle;
import com.yaho.factchecker.application.factcheck.dto.request.FactCheckStartRequest;
import com.yaho.factchecker.application.factcheck.dto.response.FactCheckStartResponse;
import com.yaho.factchecker.domain.ai.dto.common.CountryInfo;
import com.yaho.factchecker.domain.ai.dto.common.ExtractedClaim;
import com.yaho.factchecker.domain.ai.dto.request.ClaimAnalysisRequest;
import com.yaho.factchecker.domain.ai.dto.request.EvidenceForStanceRequest;
import com.yaho.factchecker.domain.ai.dto.request.StanceAnalysisRequest;
import com.yaho.factchecker.domain.ai.dto.response.ClaimAnalysisResponse;
import com.yaho.factchecker.domain.ai.dto.response.EvidenceStanceResultResponse;
import com.yaho.factchecker.domain.ai.dto.response.StanceAnalysisResponse;
import com.yaho.factchecker.domain.claim.dto.command.ClaimCategoryCreateCommand;
import com.yaho.factchecker.domain.claim.dto.command.ClaimCountryCreateCommand;
import com.yaho.factchecker.domain.claim.dto.command.ClaimCreateCommand;
import com.yaho.factchecker.domain.claim.dto.response.ClaimCategoryResponse;
import com.yaho.factchecker.domain.claim.dto.response.ClaimResponse;
import com.yaho.factchecker.domain.claim.service.ClaimService;
import com.yaho.factchecker.domain.result.code.EvidenceSourceType;
import com.yaho.factchecker.domain.result.dto.CreateAnalysisEvidenceCommand;
import com.yaho.factchecker.domain.result.dto.CreateAnalysisResultCommand;
import com.yaho.factchecker.domain.result.dto.CreateClaimAnalysisResultCommand;
import com.yaho.factchecker.domain.result.dto.CreateScoreBreakdownCommand;
import com.yaho.factchecker.domain.result.service.AnalysisResultService;
import com.yaho.factchecker.domain.retrieval.dto.ClaimRetrievalRequest;
import com.yaho.factchecker.domain.retrieval.dto.RetrievedEvidence;
import com.yaho.factchecker.domain.retrieval.service.RetrievalService;
import com.yaho.factchecker.domain.scoring.OverallScoreAggregator;
import com.yaho.factchecker.domain.scoring.ScoreCalculator;
import com.yaho.factchecker.domain.scoring.dto.ClaimScoreInput;
import com.yaho.factchecker.domain.scoring.dto.EvidenceJudgment;
import com.yaho.factchecker.domain.scoring.dto.OverallScoreCalculationResult;
import com.yaho.factchecker.domain.scoring.dto.ScoreCalculationResult;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.global.type.InputType;
import com.yaho.factchecker.global.type.Verdict;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FactCheckOrchestratorService {

    private final ClaimAnalysisPort claimAnalysisPort;
    private final ClaimService claimService;
    private final RetrievalService retrievalService;
    private final StanceAnalysisPort stanceAnalysisPort;
    private final ScoreCalculator scoreCalculator;
    private final OverallScoreAggregator overallScoreAggregator;
    private final AnalysisResultService analysisResultService;

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

        List<ClaimScoreInput> claimScoreInputs = toClaimScoreInputs(analysisBundles);

        OverallScoreCalculationResult overallScore =
                overallScoreAggregator.aggregate(claimScoreInputs);

        UUID analysisResultId = analysisResultService.create(
                toCreateAnalysisResultCommand(
                        request,
                        analysisResponse,
                        analysisBundles,
                        claimScoreInputs,
                        overallScore
                )
        );

        return FactCheckStartResponse.builder()
                .factCheckId(factCheckId)
                .analysisResultId(analysisResultId)
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

    private CreateAnalysisResultCommand toCreateAnalysisResultCommand(
            FactCheckStartRequest request,
            ClaimAnalysisResponse analysisResponse,
            List<ClaimAnalysisBundle> analysisBundles,
            List<ClaimScoreInput> claimScoreInputs,
            OverallScoreCalculationResult overallScore
    ) {
        return new CreateAnalysisResultCommand(
                null,
                InputType.TEXT,
                analysisResponse.originalText(),
                null,
                overallScore.finalScore() == null ? null : overallScore.finalScore().doubleValue(),
                overallScore.verdict(),
                request.inputText(),
                "팩트체크 분석이 완료되었습니다.",
                null,
                null,
                overallScore.scoringVersion(),
                toClaimResultCommands(analysisBundles, claimScoreInputs)
        );
    }

    private List<CreateClaimAnalysisResultCommand> toClaimResultCommands(
            List<ClaimAnalysisBundle> analysisBundles,
            List<ClaimScoreInput> claimScoreInputs
    ) {
        Map<UUID, ClaimScoreInput> scoreInputByClaimId = claimScoreInputs.stream()
                .collect(Collectors.toMap(
                        ClaimScoreInput::claimId,
                        Function.identity()
                ));

        return IntStream.range(0, analysisBundles.size())
                .mapToObj(index -> {
                    ClaimAnalysisBundle bundle = analysisBundles.get(index);
                    ClaimScoreInput scoreInput = scoreInputByClaimId.get(bundle.claim().claimId());
                    ScoreCalculationResult scoreResult = scoreInput.scoreResult();

                    return new CreateClaimAnalysisResultCommand(
                            bundle.claim().claimId(),
                            bundle.claim().canonicalClaim(),
                            index + 1,
                            scoreResult.finalScore() == null ? null : scoreResult.finalScore().doubleValue(),
                            scoreResult.verdict(),
                            "소주장 분석이 완료되었습니다.",
                            null,
                            toScoreBreakdownCommand(scoreResult),
                            toEvidenceCommands(bundle)
                    );
                })
                .toList();
    }

    private CreateScoreBreakdownCommand toScoreBreakdownCommand(ScoreCalculationResult scoreResult) {
        if (scoreResult.verdict() == Verdict.INSUFFICIENT) {
            return null;
        }

        return new CreateScoreBreakdownCommand(
                scoreResult.normalizedScore() == null ? 0.0 : scoreResult.normalizedScore(),
                0.0,
                scoreResult.validEvidenceCount() == 0 ? 0.0 : 1.0,
                0.0,
                0.0
        );
    }

    private List<CreateAnalysisEvidenceCommand> toEvidenceCommands(ClaimAnalysisBundle bundle) {
        Map<UUID, EvidenceStanceResultResponse> stanceByEvidenceId =
                bundle.stanceAnalysis().evidences().stream()
                        .collect(Collectors.toMap(
                                EvidenceStanceResultResponse::evidenceDocumentId,
                                Function.identity()
                        ));

        return IntStream.range(0, bundle.retrievedEvidences().size())
                .mapToObj(index -> {
                    RetrievedEvidence evidence = bundle.retrievedEvidences().get(index);
                    EvidenceStanceResultResponse stanceResult =
                            stanceByEvidenceId.get(evidence.evidenceDocumentId());

                    if (stanceResult == null) {
                        throw new IllegalStateException(
                                "stance 결과가 없는 evidence입니다. evidenceDocumentId="
                                        + evidence.evidenceDocumentId()
                        );
                    }

                    return new CreateAnalysisEvidenceCommand(
                            null,
                            EvidenceSourceType.PUBLIC_API,
                            evidence.title(),
                            evidence.originalUrl(),
                            evidence.evidenceDocumentId().toString(),
                            evidence.contentCleaned(),
                            stanceResult.stance(),
                            evidence.relevanceScore(),
                            evidence.similarityScore(),
                            stanceResult.reason(),
                            evidence.publishedAt(),
                            index + 1
                    );
                })
                .toList();
    }

    private List<ClaimScoreInput> toClaimScoreInputs(List<ClaimAnalysisBundle> analysisBundles) {
        return analysisBundles.stream()
                .map(bundle -> {
                    List<EvidenceJudgment> evidenceJudgments = toEvidenceJudgments(bundle);
                    ScoreCalculationResult scoreResult = scoreCalculator.calculate(evidenceJudgments);

                    return ClaimScoreInput.of(
                            bundle.claim().claimId(),
                            bundle.claim().canonicalClaim(),
                            scoreResult
                    );
                })
                .toList();
    }

    private List<EvidenceJudgment> toEvidenceJudgments(ClaimAnalysisBundle bundle) {
        Map<UUID, EvidenceStanceResultResponse> stanceByEvidenceId =
                bundle.stanceAnalysis().evidences().stream()
                        .collect(Collectors.toMap(
                                EvidenceStanceResultResponse::evidenceDocumentId,
                                Function.identity()
                        ));

        return bundle.retrievedEvidences().stream()
                .map(evidence -> {
                    EvidenceStanceResultResponse stanceResult =
                            stanceByEvidenceId.get(evidence.evidenceDocumentId());

                    if (stanceResult == null) {
                        throw new IllegalStateException(
                                "stance 결과가 없는 evidence입니다. evidenceDocumentId="
                                        + evidence.evidenceDocumentId()
                        );
                    }

                    return EvidenceJudgment.of(
                            evidence.evidenceDocumentId(),
                            stanceResult.stance(),
                            evidence.relevanceScore(),
                            evidence.similarityScore()
                    );
                })
                .toList();
    }
}
