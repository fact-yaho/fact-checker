package com.yaho.factchecker.domain.ai.dto.response;

import com.yaho.factchecker.global.type.Stance;

public record EvidenceStanceResultResponse(
        Integer idx,      // 입력 evidence의 idx (LLM이 그대로 반환)
        Stance stance,
        String reason
) {
}
