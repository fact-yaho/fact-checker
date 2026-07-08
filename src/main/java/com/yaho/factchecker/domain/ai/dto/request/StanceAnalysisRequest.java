package com.yaho.factchecker.domain.ai.dto.request;

import java.util.List;

public record StanceAnalysisRequest(
        String canonicalClaim,
        List<EvidenceForStanceRequest> evidences
) {
}
