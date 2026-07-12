package com.yaho.factchecker.domain.ai.dto.request;

import java.time.LocalDate;

public record EvidenceForStanceRequest(
        int idx,              // LLM이 참조할 짧은 번호 (1부터). UUID는 LLM에 노출하지 않음
        String title,
        String content,
        LocalDate publishedAt
) {
}
