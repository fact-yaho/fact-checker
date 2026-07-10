package com.yaho.factchecker.application.factcheck.dto.response;

import java.util.UUID;
import lombok.Builder;

@Builder
public record FactCheckStartResponse(
        UUID factCheckId,
        UUID analysisResultId
) {
}
