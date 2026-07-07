package com.yaho.factchecker.domain.result.repos;

import com.yaho.factchecker.domain.result.code.AnalysisStatus;
import com.yaho.factchecker.domain.result.entity.AnalysisResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {

    Optional<AnalysisResult> findFirstByClaimIdAndAnalysisStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
        UUID claimId,
        AnalysisStatus analysisStatus
    );

    List<AnalysisResult> findAllByClaimIdInAndAnalysisStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
        List<UUID> claimIds,
        AnalysisStatus analysisStatus
    );
}
