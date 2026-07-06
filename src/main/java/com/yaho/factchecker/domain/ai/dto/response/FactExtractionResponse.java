package com.yaho.factchecker.domain.ai.dto.response;

import java.util.List;

public record FactExtractionResponse(
        List<String> facts
) {
}
