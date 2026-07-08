package com.yaho.factchecker.domain.scoring;

import com.yaho.factchecker.domain.scoring.dto.EvidenceJudgment;
import com.yaho.factchecker.domain.scoring.dto.ScoreCalculationResult;
import java.util.List;

/**
 * 소주장에 대한 AI의 분석과 판단 근거들을 통한 점수 산출<br>
 * Orchestrator는 이 인터페이스를 바라보면 됩니다.
  */
public interface ScoreCalculator {

    ScoreCalculationResult calculate(List<EvidenceJudgment> evidences);
}
