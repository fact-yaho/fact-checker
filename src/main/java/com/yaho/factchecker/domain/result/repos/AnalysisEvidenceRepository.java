package com.yaho.factchecker.domain.result.repos;

import com.yaho.factchecker.domain.result.entity.AnalysisEvidence;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisEvidenceRepository extends JpaRepository<AnalysisEvidence, UUID> {

    List<AnalysisEvidence> findAllByAnalysisResultId(UUID analysisResultId);

    List<AnalysisEvidence> findAllByAnalysisResultIdInAndDeletedAtIsNullOrderByDisplayOrderAsc(
        List<UUID> analysisResultIds
    );
}
