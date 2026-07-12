package com.yaho.factchecker.domain.result.service;

import com.yaho.factchecker.domain.result.dto.AnalysisResultDetailResponse;
import com.yaho.factchecker.domain.result.dto.CreateAnalysisEvidenceCommand;
import com.yaho.factchecker.domain.result.dto.CreateAnalysisResultCommand;
import com.yaho.factchecker.domain.result.dto.CreateClaimAnalysisResultCommand;
import com.yaho.factchecker.domain.result.dto.CreateScoreBreakdownCommand;
import com.yaho.factchecker.domain.result.entity.AnalysisEvidence;
import com.yaho.factchecker.domain.result.entity.AnalysisResult;
import com.yaho.factchecker.domain.result.entity.ClaimAnalysisResult;
import com.yaho.factchecker.domain.result.entity.ScoreBreakdown;
import com.yaho.factchecker.domain.result.repos.AnalysisEvidenceRepository;
import com.yaho.factchecker.domain.result.repos.AnalysisResultRepository;
import com.yaho.factchecker.domain.result.repos.ClaimAnalysisResultRepository;
import com.yaho.factchecker.domain.result.repos.ScoreBreakdownRepository;
import com.yaho.factchecker.global.type.Verdict;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

//TODO : exception 변환
@Service
@RequiredArgsConstructor
public class AnalysisResultService {

    static final double INSUFFICIENT_DEFAULT_SCORE = 0.0;
    private static final String DEFAULT_INSUFFICIENT_EXPLANATION =
        "관련 공식 근거를 찾지 못해 판단할 수 없습니다.";

    private final AnalysisResultRepository analysisResultRepository;
    private final ClaimAnalysisResultRepository claimAnalysisResultRepository;
    private final ScoreBreakdownRepository scoreBreakdownRepository;
    private final AnalysisEvidenceRepository analysisEvidenceRepository;

    @Transactional
    public UUID create(CreateAnalysisResultCommand command) {
        validateCreateCommand(command);

        AnalysisResult result = AnalysisResult.builder()
            .userId(command.userId())
            .inputType(command.inputType())
            .originalInput(command.originalInput())
            .sourceUrl(command.sourceUrl())
            .finalScore(resolveOverallFinalScore(command))
            .verdict(command.verdict())
            .questionSummary(command.questionSummary())
            .answerSummary(command.answerSummary())
            .explanation(resolveOverallExplanation(command))
            .modelVersion(command.modelVersion())
            .scoringVersion(command.scoringVersion())
            .build();

        AnalysisResult savedResult = analysisResultRepository.save(result);

        for (CreateClaimAnalysisResultCommand claimCommand : command.claimResults()) {
            ClaimAnalysisResult savedClaimResult = saveClaimResult(savedResult, claimCommand);
            saveScoreBreakdown(savedClaimResult, claimCommand);
            saveEvidences(savedClaimResult, claimCommand);
        }

        return savedResult.getId();
    }

    @Transactional
    public void delete(UUID analysisResultId) {
        validateDeleteCommand(analysisResultId);

        AnalysisResult analysisResult = analysisResultRepository.findById(analysisResultId)
            .orElseThrow(() -> new IllegalArgumentException("분석 결과를 찾을 수 없습니다. id=" + analysisResultId));

        List<ClaimAnalysisResult> claimResults =
            claimAnalysisResultRepository.findAllByAnalysisResultIdAndDeletedAtIsNullOrderByDisplayOrderAsc(
                analysisResultId
            );

        List<UUID> claimResultIds = claimResults.stream()
            .map(ClaimAnalysisResult::getId)
            .toList();

        if (!claimResultIds.isEmpty()) {
            List<AnalysisEvidence> evidences =
                analysisEvidenceRepository.findAllByClaimAnalysisResultIdInAndDeletedAtIsNullOrderByDisplayOrderAsc(
                    claimResultIds
                );

            analysisEvidenceRepository.deleteAll(evidences);

            List<ScoreBreakdown> scoreBreakdowns =
                scoreBreakdownRepository.findAllByClaimAnalysisResultIdInAndDeletedAtIsNull(
                    claimResultIds
                );

            scoreBreakdownRepository.deleteAll(scoreBreakdowns);
        }

        claimAnalysisResultRepository.deleteAll(claimResults);
        analysisResultRepository.delete(analysisResult);
    }

    @Transactional
    public void deleteByUser(UUID analysisResultId, UUID userId) {
        if (analysisResultId == null) {
            throw new IllegalArgumentException("분석 결과 ID는 필수입니다.");
        }

        AnalysisResult analysisResult = analysisResultRepository.findById(analysisResultId)
                .orElseThrow(() -> new IllegalArgumentException("분석 결과를 찾을 수 없습니다. id=" + analysisResultId));

        // 소유권 검증: 본인 검증 기록만 삭제 가능
        if (userId == null || analysisResult.getUserId() == null
                || !analysisResult.getUserId().equals(userId)) {
            throw new SecurityException("본인의 검증 기록만 삭제할 수 있습니다.");
        }

        delete(analysisResultId); // 기존 삭제 로직(소주장·근거·점수까지) 그대로 재사용
    }

    // Helper Methods =========================================================================
    private ClaimAnalysisResult saveClaimResult(
        AnalysisResult savedResult,
        CreateClaimAnalysisResultCommand claimCommand
    ) {
        validateClaimCommand(claimCommand);

        ClaimAnalysisResult claimResult = ClaimAnalysisResult.builder()
            .analysisResult(savedResult)
            .claimId(claimCommand.claimId())
            .claimText(claimCommand.claimText())
            .displayOrder(claimCommand.displayOrder())
            .finalScore(resolveClaimFinalScore(claimCommand))
            .verdict(claimCommand.verdict())
            .answerSummary(claimCommand.answerSummary())
            .explanation(resolveClaimExplanation(claimCommand))
            .build();

        return claimAnalysisResultRepository.save(claimResult);
    }

    private void saveScoreBreakdown(
        ClaimAnalysisResult savedClaimResult,
        CreateClaimAnalysisResultCommand claimCommand
    ) {
        CreateScoreBreakdownCommand scoreCommand = resolveScoreBreakdownCommand(claimCommand);
        Double finalScore = resolveClaimFinalScore(claimCommand);

        ScoreBreakdown scoreBreakdown = ScoreBreakdown.builder()
            .claimAnalysisResult(savedClaimResult)
            .officialConsistencyScore(scoreCommand.officialConsistencyScore())
            .evidenceRelevanceScore(scoreCommand.evidenceRelevanceScore())
            .evidenceSufficiencyScore(scoreCommand.evidenceSufficiencyScore())
            .recencyScore(scoreCommand.recencyScore())
            .contradictionPenalty(scoreCommand.contradictionPenalty())
            .finalScore(finalScore)
            .build();

        scoreBreakdownRepository.save(scoreBreakdown);
    }

    private void saveEvidences(
        ClaimAnalysisResult savedClaimResult,
        CreateClaimAnalysisResultCommand claimCommand
    ) {
        if (claimCommand.evidences().isEmpty()) {
            return;
        }

        List<AnalysisEvidence> evidences = claimCommand.evidences().stream()
            .map(evidenceCommand -> toEvidence(savedClaimResult, evidenceCommand))
            .toList();

        analysisEvidenceRepository.saveAll(evidences);
    }

    private AnalysisEvidence toEvidence(
        ClaimAnalysisResult savedClaimResult,
        CreateAnalysisEvidenceCommand evidenceCommand
    ) {
        return AnalysisEvidence.builder()
            .claimAnalysisResult(savedClaimResult)
            .aiLogId(evidenceCommand.aiLogId())
            .sourceType(evidenceCommand.sourceType())
            .sourceTitle(evidenceCommand.sourceTitle())
            .sourceUrl(evidenceCommand.sourceUrl())
            .sourceId(evidenceCommand.sourceId())
            .snippet(evidenceCommand.snippet())
            .stance(evidenceCommand.stance())
            .relevanceScore(evidenceCommand.relevanceScore())
            .similarityScore(evidenceCommand.similarityScore())
            .judgmentReason(evidenceCommand.judgmentReason())
            .publishedAt(evidenceCommand.publishedAt())
            .displayOrder(evidenceCommand.displayOrder())
            .build();
    }

    private void validateCreateCommand(CreateAnalysisResultCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("분석 결과 생성 요청은 null일 수 없습니다.");
        }

        if (command.originalInput() == null || command.originalInput().isBlank()) {
            throw new IllegalArgumentException("사용자 입력 원문은 필수입니다.");
        }

        if (command.verdict() == null) {
            throw new IllegalArgumentException("최종 종합 판정은 필수입니다.");
        }

        // 🔧 점수 없이 저장 가능한 판정: INSUFFICIENT(근거 부족), OUT_OF_SCOPE(검증 대상 아님)
        if (command.finalScore() == null && !isUnscoredVerdict(command.verdict())) {
            throw new IllegalArgumentException("최종 종합 점수는 필수입니다.");
        }

        if (command.claimResults() == null) {
            throw new IllegalArgumentException("소주장 분석 결과 목록은 null일 수 없습니다.");
        }

        // 🔧 소주장이 비어도 되는 경우는 OUT_OF_SCOPE(검증 가능한 주장이 없는 입력)뿐
        if (command.claimResults().isEmpty() && command.verdict() != Verdict.OUT_OF_SCOPE) {
            throw new IllegalArgumentException("소주장 분석 결과는 최소 1개 이상 필요합니다.");
        }

        command.claimResults().forEach(this::validateClaimCommand);
    }

    // 🔧 추가: 점수를 매기지 않는 판정
    private boolean isUnscoredVerdict(Verdict verdict) {
        return verdict == Verdict.INSUFFICIENT || verdict == Verdict.OUT_OF_SCOPE;
    }

    private void validateClaimCommand(CreateClaimAnalysisResultCommand claimCommand) {
        if (claimCommand == null) {
            throw new IllegalArgumentException("소주장 분석 결과는 null일 수 없습니다.");
        }

        if (claimCommand.claimText() == null || claimCommand.claimText().isBlank()) {
            throw new IllegalArgumentException("소주장 텍스트는 필수입니다.");
        }

        if (claimCommand.displayOrder() == null) {
            throw new IllegalArgumentException("소주장 표시 순서는 필수입니다.");
        }

        if (claimCommand.verdict() == null) {
            throw new IllegalArgumentException("소주장 판정은 필수입니다.");
        }

        if (claimCommand.finalScore() == null && claimCommand.verdict() != Verdict.INSUFFICIENT) {
            throw new IllegalArgumentException("소주장 최종 점수는 필수입니다.");
        }

        if (claimCommand.scoreBreakdown() == null && claimCommand.verdict() != Verdict.INSUFFICIENT) {
            throw new IllegalArgumentException("소주장 점수 상세 정보는 필수입니다.");
        }

        if (claimCommand.evidences() == null) {
            throw new IllegalArgumentException("소주장별 분석 근거 목록은 null일 수 없습니다.");
        }

        if (claimCommand.evidences().isEmpty() && claimCommand.verdict() != Verdict.INSUFFICIENT) {
            throw new IllegalArgumentException("근거가 없는 경우 소주장 판정은 INSUFFICIENT여야 합니다.");
        }
    }

    private void validateDeleteCommand(UUID analysisResultId) {
        if (analysisResultId == null) {
            throw new IllegalArgumentException("분석 결과 ID는 필수입니다.");
        }
    }

    private Double resolveOverallFinalScore(CreateAnalysisResultCommand command) {
        if (command.finalScore() != null) {
            return command.finalScore();
        }

        return INSUFFICIENT_DEFAULT_SCORE;
    }

    private Double resolveClaimFinalScore(CreateClaimAnalysisResultCommand claimCommand) {
        if (claimCommand.finalScore() != null) {
            return claimCommand.finalScore();
        }

        return INSUFFICIENT_DEFAULT_SCORE;
    }

    private CreateScoreBreakdownCommand resolveScoreBreakdownCommand(
        CreateClaimAnalysisResultCommand claimCommand
    ) {
        if (claimCommand.scoreBreakdown() != null) {
            return claimCommand.scoreBreakdown();
        }

        return createInsufficientScoreBreakdown();
    }

    private CreateScoreBreakdownCommand createInsufficientScoreBreakdown() {
        return new CreateScoreBreakdownCommand(
            INSUFFICIENT_DEFAULT_SCORE,
            INSUFFICIENT_DEFAULT_SCORE,
            INSUFFICIENT_DEFAULT_SCORE,
            INSUFFICIENT_DEFAULT_SCORE,
            INSUFFICIENT_DEFAULT_SCORE
        );
    }

    private String resolveOverallExplanation(CreateAnalysisResultCommand command) {
        if (!isBlank(command.explanation())) {
            return command.explanation();
        }

        if (command.verdict() == Verdict.INSUFFICIENT) {
            return DEFAULT_INSUFFICIENT_EXPLANATION;
        }

        return command.explanation();
    }

    private String resolveClaimExplanation(CreateClaimAnalysisResultCommand claimCommand) {
        if (!isBlank(claimCommand.explanation())) {
            return claimCommand.explanation();
        }

        if (claimCommand.verdict() == Verdict.INSUFFICIENT) {
            return DEFAULT_INSUFFICIENT_EXPLANATION;
        }

        return claimCommand.explanation();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
