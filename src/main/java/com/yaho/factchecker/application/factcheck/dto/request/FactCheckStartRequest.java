package com.yaho.factchecker.application.factcheck.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record FactCheckStartRequest(
        @NotBlank(message = "검증할 내용을 입력해주세요.")
        String inputText
) {
}
