package com.yaho.factchecker.global.type;

public enum IngestionFailureStage {
    FETCH,   // API 페이지 조회 실패
    CLEAN,   // 본문 정제 실패
    WRITE    // fact 추출·임베딩·저장 실패 (DocumentFactWriter)
}
