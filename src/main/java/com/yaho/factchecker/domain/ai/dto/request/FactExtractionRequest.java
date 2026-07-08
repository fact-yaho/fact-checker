package com.yaho.factchecker.domain.ai.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FactExtractionRequest(
        @NotBlank(message = "fact 추출할 문서 본문은 필수입니다.")
        String documentContent
) {
}
