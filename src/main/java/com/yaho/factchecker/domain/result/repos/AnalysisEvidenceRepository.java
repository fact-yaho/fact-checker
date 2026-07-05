package com.yaho.factchecker.domain.result.repos;

import com.yaho.factchecker.domain.result.entity.AnalysisEvidence;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisEvidenceRepository extends JpaRepository<AnalysisEvidence, UUID> {

}
