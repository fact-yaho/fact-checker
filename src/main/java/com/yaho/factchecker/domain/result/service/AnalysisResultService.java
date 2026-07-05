package com.yaho.factchecker.domain.result.service;

import com.yaho.factchecker.domain.result.dto.CreateAnalysisEvidenceCommand;
import com.yaho.factchecker.domain.result.dto.CreateAnalysisResultCommand;
import com.yaho.factchecker.domain.result.entity.AnalysisEvidence;
import com.yaho.factchecker.domain.result.entity.AnalysisResult;
import com.yaho.factchecker.domain.result.entity.ScoreBreakdown;
import com.yaho.factchecker.domain.result.repos.AnalysisEvidenceRepository;
import com.yaho.factchecker.domain.result.repos.AnalysisResultRepository;
import com.yaho.factchecker.domain.result.repos.ScoreBreakdownRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

//TODO : exception 변환
@Service
@RequiredArgsConstructor
public class AnalysisResultService {

    private final AnalysisResultRepository analysisResultRepository;
    private final ScoreBreakdownRepository scoreBreakdownRepository;
    private final AnalysisEvidenceRepository analysisEvidenceRepository;

    @Transactional
    public UUID create(CreateAnalysisResultCommand command) {
        validateCreateCommand(command);

        AnalysisResult result = AnalysisResult.builder()
            .userId(command.userId())
            .inputType(command.inputType())
            .originalInput(command.originalInput())
            .claimText(command.claimText())
            .sourceUrl(command.sourceUrl())
            .finalScore(command.finalScore())
            .verdict(command.verdict())
            .questionSummary(command.questionSummary())
            .answerSummary(command.answerSummary())
            .explanation(command.explanation())
            .modelVersion(command.modelVersion())
            .scoringVersion(command.scoringVersion())
            .build();

        AnalysisResult savedResult = analysisResultRepository.save(result);

        ScoreBreakdown scoreBreakdown = ScoreBreakdown.builder()
            .analysisResult(savedResult)
            .officialConsistencyScore(command.scoreBreakdown().officialConsistencyScore())
            .evidenceRelevanceScore(command.scoreBreakdown().evidenceRelevanceScore())
            .evidenceSufficiencyScore(command.scoreBreakdown().evidenceSufficiencyScore())
            .recencyScore(command.scoreBreakdown().recencyScore())
            .contradictionPenalty(command.scoreBreakdown().contradictionPenalty())
            .finalScore(command.finalScore())
            .build();

        scoreBreakdownRepository.save(scoreBreakdown);

        List<AnalysisEvidence> evidences = command.evidences().stream()
            .map(evidenceCommand -> AnalysisEvidence.builder()
                .analysisResult(savedResult)
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
                .build())
            .toList();

        analysisEvidenceRepository.saveAll(evidences);

        return savedResult.getId();
    }

    private void validateCreateCommand(CreateAnalysisResultCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("분석 결과 생성 요청은 null일 수 없습니다.");
        }

        if (command.claimText() == null || command.claimText().isBlank()) {
            throw new IllegalArgumentException("분석 대상 주장은 필수입니다.");
        }

        if (command.finalScore() == null) {
            throw new IllegalArgumentException("최종 점수는 필수입니다.");
        }

        if (command.verdict() == null) {
            throw new IllegalArgumentException("최종 판정은 필수입니다.");
        }

        if (command.scoreBreakdown() == null) {
            throw new IllegalArgumentException("점수 상세 정보는 필수입니다.");
        }

        if (command.evidences() == null || command.evidences().isEmpty()) {
            throw new IllegalArgumentException("분석 근거는 최소 1개 이상 필요합니다.");
        }
    }
}
