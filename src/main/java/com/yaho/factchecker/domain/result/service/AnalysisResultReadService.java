package com.yaho.factchecker.domain.result.service;

import com.yaho.factchecker.domain.result.code.AnalysisStatus;
import com.yaho.factchecker.domain.result.dto.AnalysisEvidenceResponse;
import com.yaho.factchecker.domain.result.dto.AnalysisResultDetailResponse;
import com.yaho.factchecker.domain.result.dto.AnalysisResultSummaryResponse;
import com.yaho.factchecker.domain.result.dto.ClaimAnalysisResultResponse;
import com.yaho.factchecker.domain.result.dto.ScoreBreakdownResponse;
import com.yaho.factchecker.domain.result.entity.AnalysisEvidence;
import com.yaho.factchecker.domain.result.entity.AnalysisResult;
import com.yaho.factchecker.domain.result.entity.ClaimAnalysisResult;
import com.yaho.factchecker.domain.result.entity.ScoreBreakdown;
import com.yaho.factchecker.domain.result.repos.AnalysisEvidenceRepository;
import com.yaho.factchecker.domain.result.repos.AnalysisResultRepository;
import com.yaho.factchecker.domain.result.repos.ClaimAnalysisResultRepository;
import com.yaho.factchecker.domain.result.repos.ScoreBreakdownRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AnalysisResultReadService {

    private final AnalysisResultRepository analysisResultRepository;
    private final ClaimAnalysisResultRepository claimAnalysisResultRepository;
    private final ScoreBreakdownRepository scoreBreakdownRepository;
    private final AnalysisEvidenceRepository analysisEvidenceRepository;

    /**
     * 회원의 이전 분석 결과 목록을 조회합니다.
     *
     * 기록 탭 목록 화면에서 사용합니다.
     *
     * 이 목록은 사용자 입력 전체에 대한 종합 결과 기준입니다.
     * 즉, 소주장별 결과가 아니라 AnalysisResult 단위로 조회합니다.
     */
    public List<AnalysisResultSummaryResponse> findUserResultSummaries(
        UUID userId,
        Pageable pageable
    ) {
        validateUserId(userId);

        return analysisResultRepository
            .findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable)
            .stream()
            .map(AnalysisResultSummaryResponse::from)
            .toList();
    }

    /**
     * 회원의 특정 분석 결과 상세를 조회합니다.
     *
     * 상세 응답 구조:
     * AnalysisResult
     * └─ ClaimAnalysisResult 목록
     *    ├─ ScoreBreakdown
     *    └─ AnalysisEvidence 목록
     */
    public AnalysisResultDetailResponse getUserResultDetail(
        UUID userId,
        UUID resultId
    ) {
        validateResultId(resultId);

        AnalysisResult result = analysisResultRepository
            .findByIdAndUserIdAndDeletedAtIsNull(resultId, userId)
            .orElseThrow(() -> new IllegalArgumentException(
                "분석 결과를 찾을 수 없습니다. resultId=" + resultId
            ));

        return toDetailResponse(result);
    }

    /**
     * 단일 claim_id 기준으로 가장 최근 완료된 소주장 분석 결과를 조회합니다.
     *
     * 기존에는 claim_id가 AnalysisResult에 있었기 때문에 AnalysisResultDetailResponse를 반환했지만,
     * 이제 claim_id는 ClaimAnalysisResult에 있으므로 소주장 단위 응답을 반환합니다.
     */
    public Optional<ClaimAnalysisResultResponse> findReusableClaimResultByClaimId(UUID claimId) {
        validateClaimId(claimId);

        return claimAnalysisResultRepository
            .findFirstByClaimIdAndAnalysisResult_AnalysisStatusAndDeletedAtIsNullAndAnalysisResult_DeletedAtIsNullOrderByCreatedAtDesc(
                claimId,
                AnalysisStatus.COMPLETED
            )
            .map(this::toClaimResponse);
    }

    /**
     * 여러 claim_id 기준으로 재사용 가능한 완료 소주장 분석 결과들을 조회합니다.
     *
     * 유사 질문 탐색 결과로 나온 claim_id 목록을 넘겨받는 용도입니다.
     */
    public List<ClaimAnalysisResultResponse> findReusableClaimResultsByClaimIds(List<UUID> claimIds) {
        validateClaimIds(claimIds);

        List<ClaimAnalysisResult> claimResults =
            claimAnalysisResultRepository
                .findAllByClaimIdInAndAnalysisResult_AnalysisStatusAndDeletedAtIsNullAndAnalysisResult_DeletedAtIsNullOrderByCreatedAtDesc(
                    claimIds,
                    AnalysisStatus.COMPLETED
                );

        return toClaimResponses(claimResults);
    }

    // Helper Methods =========================================================================

    private AnalysisResultDetailResponse toDetailResponse(AnalysisResult result) {
        List<ClaimAnalysisResult> claimResults =
            claimAnalysisResultRepository
                .findAllByAnalysisResultIdAndDeletedAtIsNullOrderByDisplayOrderAsc(
                    result.getId()
                );

        List<ClaimAnalysisResultResponse> claimResponses = toClaimResponses(claimResults);

        return AnalysisResultDetailResponse.of(result, claimResponses);
    }

    private ClaimAnalysisResultResponse toClaimResponse(ClaimAnalysisResult claimResult) {
        ScoreBreakdown scoreBreakdown =
            scoreBreakdownRepository
                .findByClaimAnalysisResultIdAndDeletedAtIsNull(claimResult.getId())
                .orElse(null);

        List<AnalysisEvidence> evidences =
            analysisEvidenceRepository
                .findAllByClaimAnalysisResultIdAndDeletedAtIsNullOrderByDisplayOrderAsc(
                    claimResult.getId()
                );

        return ClaimAnalysisResultResponse.of(
            claimResult,
            ScoreBreakdownResponse.from(scoreBreakdown),
            evidences.stream()
                .map(AnalysisEvidenceResponse::from)
                .toList()
        );
    }

    private List<ClaimAnalysisResultResponse> toClaimResponses(
        List<ClaimAnalysisResult> claimResults
    ) {
        if (claimResults == null || claimResults.isEmpty()) {
            return List.of();
        }

        List<ClaimAnalysisResult> sortedClaimResults = claimResults.stream()
            .sorted(Comparator.comparing(ClaimAnalysisResult::getDisplayOrder))
            .toList();

        List<UUID> claimResultIds = sortedClaimResults.stream()
            .map(ClaimAnalysisResult::getId)
            .toList();

        Map<UUID, ScoreBreakdown> scoreBreakdownMap =
            scoreBreakdownRepository
                .findAllByClaimAnalysisResultIdInAndDeletedAtIsNull(claimResultIds)
                .stream()
                .collect(Collectors.toMap(
                    scoreBreakdown -> scoreBreakdown.getClaimAnalysisResult().getId(),
                    scoreBreakdown -> scoreBreakdown
                ));

        Map<UUID, List<AnalysisEvidence>> evidenceMap =
            analysisEvidenceRepository
                .findAllByClaimAnalysisResultIdInAndDeletedAtIsNullOrderByDisplayOrderAsc(
                    claimResultIds
                )
                .stream()
                .collect(Collectors.groupingBy(
                    evidence -> evidence.getClaimAnalysisResult().getId()
                ));

        return sortedClaimResults.stream()
            .map(claimResult -> ClaimAnalysisResultResponse.of(
                claimResult,
                ScoreBreakdownResponse.from(scoreBreakdownMap.get(claimResult.getId())),
                evidenceMap.getOrDefault(claimResult.getId(), List.of())
                    .stream()
                    .sorted(Comparator.comparing(AnalysisEvidence::getDisplayOrder))
                    .map(AnalysisEvidenceResponse::from)
                    .toList()
            ))
            .toList();
    }

    private void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
    }

    private void validateResultId(UUID resultId) {
        if (resultId == null) {
            throw new IllegalArgumentException("resultId는 필수입니다.");
        }
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
