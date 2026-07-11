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
import com.yaho.factchecker.domain.result.dto.AnalysisResultDetailResponse;
import com.yaho.factchecker.domain.result.dto.CreateAnalysisEvidenceCommand;
import com.yaho.factchecker.domain.result.dto.CreateAnalysisResultCommand;
import com.yaho.factchecker.domain.result.dto.CreateClaimAnalysisResultCommand;
import com.yaho.factchecker.domain.result.dto.CreateScoreBreakdownCommand;
import com.yaho.factchecker.domain.result.service.AnalysisResultReadService;
import com.yaho.factchecker.domain.result.service.AnalysisResultService;
import com.yaho.factchecker.domain.retrieval.dto.ClaimRetrievalRequest;
import com.yaho.factchecker.domain.retrieval.dto.RetrievedEvidence;
import com.yaho.factchecker.domain.retrieval.service.RetrievalService;
import com.yaho.factchecker.domain.scoring.OverallScoreAggregator;
import com.yaho.factchecker.domain.scoring.ScoreCalculator;
import com.yaho.factchecker.domain.scoring.dto.ClaimScoreInput;
import com.yaho.factchecker.domain.scoring.dto.EvidenceJudgment;
import com.yaho.factchecker.domain.scoring.dto.EvidenceScore;
import com.yaho.factchecker.domain.scoring.dto.OverallScoreCalculationResult;
import com.yaho.factchecker.domain.scoring.dto.ScoreCalculationResult;
import com.yaho.factchecker.global.type.ClaimCategory;
import com.yaho.factchecker.global.type.InputType;
import com.yaho.factchecker.global.type.Stance;
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
    private final AnalysisResultReadService analysisResultReadService;

    public AnalysisResultDetailResponse start(FactCheckStartRequest request, UUID userId) {
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

        if (analysisBundles.isEmpty()) {
            UUID outOfScopeResultId = analysisResultService.create(
                    toOutOfScopeResultCommand(userId, request, analysisResponse)
            );
            return analysisResultReadService.getResultDetailById(outOfScopeResultId);
        }

        List<ClaimScoreInput> claimScoreInputs = toClaimScoreInputs(analysisBundles);

        OverallScoreCalculationResult overallScore =
                overallScoreAggregator.aggregate(claimScoreInputs);

        UUID analysisResultId = analysisResultService.create(
                toCreateAnalysisResultCommand(
                        userId,
                        request,
                        analysisResponse,
                        analysisBundles,
                        claimScoreInputs,
                        overallScore
                )
        );

        return analysisResultReadService.getResultDetailById(analysisResultId);
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

    private CreateAnalysisResultCommand toOutOfScopeResultCommand(
            UUID userId,
            FactCheckStartRequest request,
            ClaimAnalysisResponse analysisResponse
    ) {
        return new CreateAnalysisResultCommand(
                userId,
                InputType.TEXT,
                analysisResponse.originalText(),
                null,
                null,   // finalScore: 점수 없음(서비스가 0.0으로 기본 저장)
                Verdict.OUT_OF_SCOPE,   // 검증 대상 아님
                request.inputText(),    // questionSummary
                "입력에서 검증 가능한 외교 사실 주장을 찾지 못했습니다.",   // answerSummary (필수)
                "검증 가능한 사실 주장이 없어 판정을 산정하지 않았습니다.", // explanation
                null,                                              // modelVersion
                null,                                              // scoringVersion
                List.of()                                         // claimResults 비움
        );
    }

    private CreateAnalysisResultCommand toCreateAnalysisResultCommand(
            UUID userId,
            FactCheckStartRequest request,
            ClaimAnalysisResponse analysisResponse,
            List<ClaimAnalysisBundle> analysisBundles,
            List<ClaimScoreInput> claimScoreInputs,
            OverallScoreCalculationResult overallScore
    ) {
        return new CreateAnalysisResultCommand(
                userId,
                InputType.TEXT,
                analysisResponse.originalText(),
                null,
                overallScore.finalScore() == null ? null : overallScore.finalScore().doubleValue(),
                overallScore.verdict(),
                request.inputText(),
                buildOverallSummary(overallScore, claimScoreInputs),
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
                            buildClaimSummary(scoreResult, bundle),
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

        // 유효 근거만 집계 대상
        List<EvidenceScore> valid =
                (scoreResult.evidenceScores() == null ? List.<EvidenceScore>of() : scoreResult.evidenceScores())
                        .stream()
                        .filter(EvidenceScore::valid)
                        .toList();

        // 근거 관련도: 유효 근거들의 관련도 평균
        double evidenceRelevanceScore = valid.isEmpty()
                ? 0.0
                : valid.stream().mapToDouble(EvidenceScore::relevanceScore).average().orElse(0.0);

        // 모순 근거 패널티: 유효 근거 중 반박(CONTRADICTS) 비율 (0~1)
        double contradictionPenalty = valid.isEmpty()
                ? 0.0
                : (double) valid.stream().filter(e -> e.stance() == Stance.CONTRADICTS).count() / valid.size();

        return new CreateScoreBreakdownCommand(
                scoreResult.normalizedScore() == null ? 0.0 : scoreResult.normalizedScore(),
                round2(evidenceRelevanceScore),
                scoreResult.validEvidenceCount() == 0 ? 0.0 : 1.0,
                0.0,
                round2(contradictionPenalty)
        );
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String buildOverallSummary(
            OverallScoreCalculationResult overallScore,
            List<ClaimScoreInput> claimScoreInputs
    ) {
        String verdictPhrase = verdictPhrase(overallScore.verdict());

        int total = claimScoreInputs.size();
        if (total == 0) {
            return verdictPhrase + ".";
        }

        long consistent = claimScoreInputs.stream()
                .map(c -> c.scoreResult().verdict())
                .filter(v -> v == Verdict.CONSISTENT || v == Verdict.PARTIALLY_CONSISTENT)
                .count();
        long inconsistent = claimScoreInputs.stream()
                .map(c -> c.scoreResult().verdict())
                .filter(v -> v == Verdict.INCONSISTENT || v == Verdict.PARTIALLY_INCONSISTENT)
                .count();
        long insufficient = claimScoreInputs.stream()
                .map(c -> c.scoreResult().verdict())
                .filter(v -> v == Verdict.INSUFFICIENT)
                .count();

        StringBuilder detail = new StringBuilder("일치 ").append(consistent).append("건");
        if (inconsistent > 0) {
            detail.append(", 불일치 ").append(inconsistent).append("건");
        }
        if (insufficient > 0) {
            detail.append(", 근거 부족 ").append(insufficient).append("건");
        }

        return String.format("검증한 %d개 소주장 중 %s입니다. 종합적으로 %s.", total, detail, verdictPhrase);
    }

    private String buildClaimSummary(ScoreCalculationResult scoreResult, ClaimAnalysisBundle bundle) {
        int evidenceCount = bundle.stanceAnalysis().evidences().size();
        return String.format("공식 근거 %d건과 대조한 결과, %s.", evidenceCount, verdictPhrase(scoreResult.verdict()));
    }

    private String verdictPhrase(Verdict verdict) {
        return switch (verdict) {
            case CONSISTENT             -> "공식 자료와 일치하는 것으로 판단됩니다";
            case PARTIALLY_CONSISTENT   -> "공식 자료와 대체로 일치하는 것으로 판단됩니다";
            case UNCERTAIN              -> "공식 자료만으로는 판단을 유보합니다";
            case PARTIALLY_INCONSISTENT -> "공식 자료와 부분적으로 일치하지 않는 것으로 판단됩니다";
            case INCONSISTENT           -> "공식 자료와 일치하지 않는 것으로 판단됩니다";
            case INSUFFICIENT           -> "관련 공식 근거가 부족해 판단하기 어렵습니다";
            case OUT_OF_SCOPE           -> "공식 자료로 검증할 수 있는 사실 주장이 없습니다";
        };
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
