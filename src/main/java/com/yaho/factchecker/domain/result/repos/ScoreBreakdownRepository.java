package com.yaho.factchecker.domain.result.repos;

import com.yaho.factchecker.domain.result.entity.ScoreBreakdown;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreBreakdownRepository extends JpaRepository<ScoreBreakdown, UUID> {

    Optional<ScoreBreakdown> findByAnalysisResultIdAndDeletedAtIsNull(UUID analysisResultId);

    List<ScoreBreakdown> findAllByAnalysisResultIdInAndDeletedAtIsNull(
        List<UUID> analysisResultIds
    );
}
