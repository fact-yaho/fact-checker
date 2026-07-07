package com.yaho.factchecker.domain.result.service;

import com.yaho.factchecker.domain.result.code.AnalysisStatus;
import com.yaho.factchecker.domain.result.dto.AnalysisEvidenceResponse;
import com.yaho.factchecker.domain.result.dto.AnalysisResultDetailResponse;
import com.yaho.factchecker.domain.result.dto.ScoreBreakdownResponse;
import com.yaho.factchecker.domain.result.entity.AnalysisEvidence;
import com.yaho.factchecker.domain.result.entity.AnalysisResult;
import com.yaho.factchecker.domain.result.entity.ScoreBreakdown;
import com.yaho.factchecker.domain.result.repos.AnalysisEvidenceRepository;
import com.yaho.factchecker.domain.result.repos.AnalysisResultRepository;
import com.yaho.factchecker.domain.result.repos.ScoreBreakdownRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AnalysisResultReadService {

    private final AnalysisResultRepository analysisResultRepository;
    private final ScoreBreakdownRepository scoreBreakdownRepository;
    private final AnalysisEvidenceRepository analysisEvidenceRepository;

    /**
     * 단일 claim_id 기준으로 가장 최근 완료된 분석 결과를 조회합니다.
     */
    public Optional<AnalysisResultDetailResponse> findReusableResultByClaimId(UUID claimId) {
        validateClaimId(claimId);

        return analysisResultRepository
            .findFirstByClaimIdAndAnalysisStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                claimId,
                AnalysisStatus.COMPLETED
            )
            .map(this::toDetailResponse);
    }

    /**
     * 여러 claim_id 기준으로 재사용 가능한 완료 분석 결과들을 조회합니다.
     *
     * 유사 질문 탐색 결과로 나온 claim_id 목록을 넘겨받는 용도입니다.
     */
    public List<AnalysisResultDetailResponse> findReusableResultsByClaimIds(List<UUID> claimIds) {
        validateClaimIds(claimIds);

        List<AnalysisResult> results =
            analysisResultRepository.findAllByClaimIdInAndAnalysisStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                claimIds,
                AnalysisStatus.COMPLETED
            );

        if (results.isEmpty()) {
            return List.of();
        }

        List<UUID> analysisResultIds = results.stream()
            .map(AnalysisResult::getId)
            .toList();

        Map<UUID, ScoreBreakdown> scoreBreakdownMap =
            scoreBreakdownRepository.findAllByAnalysisResultIdInAndDeletedAtIsNull(analysisResultIds)
                .stream()
                .collect(Collectors.toMap(
                    scoreBreakdown -> scoreBreakdown.getAnalysisResult().getId(),
                    scoreBreakdown -> scoreBreakdown
                ));

        Map<UUID, List<AnalysisEvidence>> evidenceMap =
            analysisEvidenceRepository
                .findAllByAnalysisResultIdInAndDeletedAtIsNullOrderByDisplayOrderAsc(analysisResultIds)
                .stream()
                .collect(Collectors.groupingBy(
                    evidence -> evidence.getAnalysisResult().getId()
                ));

        return results.stream()
            .map(result -> toDetailResponse(
                result,
                scoreBreakdownMap.get(result.getId()),
                evidenceMap.getOrDefault(result.getId(), List.of())
            ))
            .toList();
    }

    private AnalysisResultDetailResponse toDetailResponse(AnalysisResult result) {
        ScoreBreakdown scoreBreakdown = scoreBreakdownRepository
            .findByAnalysisResultId(result.getId())
            .orElse(null);

        List<AnalysisEvidence> evidences = analysisEvidenceRepository
            .findAllByAnalysisResultId(result.getId())
            .stream()
            .sorted(Comparator.comparing(AnalysisEvidence::getDisplayOrder))
            .toList();

        return toDetailResponse(result, scoreBreakdown, evidences);
    }

    private AnalysisResultDetailResponse toDetailResponse(
        AnalysisResult result,
        ScoreBreakdown scoreBreakdown,
        List<AnalysisEvidence> evidences
    ) {
        return AnalysisResultDetailResponse.of(
            result,
            ScoreBreakdownResponse.from(scoreBreakdown),
            evidences.stream()
                .map(AnalysisEvidenceResponse::from)
                .toList()
        );
    }

    private void validateClaimId(UUID claimId) {
        if (claimId == null) {
            throw new IllegalArgumentException("claimId는 필수입니다.");
        }
    }

    private void validateClaimIds(List<UUID> claimIds) {
        if (claimIds == null || claimIds.isEmpty()) {
            throw new IllegalArgumentException("claimIds는 최소 1개 이상 필요합니다.");
        }

        if (claimIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("claimIds에는 null이 포함될 수 없습니다.");
        }
    }
}
