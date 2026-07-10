package com.yaho.factchecker.application.factcheck.dto.internal;

import com.yaho.factchecker.domain.ai.dto.response.StanceAnalysisResponse;
import com.yaho.factchecker.domain.claim.dto.response.ClaimResponse;
import com.yaho.factchecker.domain.retrieval.dto.RetrievedEvidence;
import java.util.List;
import lombok.Builder;

@Builder
public record ClaimAnalysisBundle(
        ClaimResponse claim,
        List<RetrievedEvidence> retrievedEvidences,
        StanceAnalysisResponse stanceAnalysis
) {
}
