package com.yaho.factchecker.domain.scoring;

import com.yaho.factchecker.domain.scoring.dto.ClaimScoreInput;
import com.yaho.factchecker.domain.scoring.dto.OverallScoreCalculationResult;
import java.util.List;

/**
 * 여러 소주장 점수를 전체 종합 점수로 합산하는 인터페이스<br>
 * Orchestrator는 이 인터페이스를 바라보면 됩니다.
 */
public interface OverallScoreAggregator {

    OverallScoreCalculationResult aggregate(List<ClaimScoreInput> claimScores);
}
