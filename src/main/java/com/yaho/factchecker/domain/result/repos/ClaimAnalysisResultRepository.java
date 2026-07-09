package com.yaho.factchecker.domain.result.repos;

import com.yaho.factchecker.domain.result.code.AnalysisStatus;
import com.yaho.factchecker.domain.result.entity.ClaimAnalysisResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimAnalysisResultRepository extends JpaRepository<ClaimAnalysisResult, UUID> {

    List<ClaimAnalysisResult> findAllByAnalysisResultIdAndDeletedAtIsNullOrderByDisplayOrderAsc(
        UUID analysisResultId
    );

    List<ClaimAnalysisResult> findAllByAnalysisResultIdInAndDeletedAtIsNullOrderByDisplayOrderAsc(
        List<UUID> analysisResultIds
    );

    Optional<ClaimAnalysisResult>
    findFirstByClaimIdAndAnalysisResult_AnalysisStatusAndDeletedAtIsNullAndAnalysisResult_DeletedAtIsNullOrderByCreatedAtDesc(
        UUID claimId,
        AnalysisStatus analysisStatus
    );

    List<ClaimAnalysisResult>
    findAllByClaimIdInAndAnalysisResult_AnalysisStatusAndDeletedAtIsNullAndAnalysisResult_DeletedAtIsNullOrderByCreatedAtDesc(
        List<UUID> claimIds,
        AnalysisStatus analysisStatus
    );
}
