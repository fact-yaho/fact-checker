package com.yaho.factchecker.domain.result.repos;

import com.yaho.factchecker.domain.result.entity.AnalysisResult;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {

}
