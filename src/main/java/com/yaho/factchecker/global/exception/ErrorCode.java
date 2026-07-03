package com.yaho.factchecker.global.exception;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    //common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 입력값입니다."),

    //ai
    AI_API_CALL_FAILED(HttpStatus.BAD_GATEWAY, "AI_001", "AI API 호출에 실패했습니다."),
    AI_EMPTY_RESPONSE(HttpStatus.BAD_GATEWAY, "AI_002", "AI 응답이 비어 있습니다."),
    AI_RESPONSE_PARSE_FAILED(HttpStatus.BAD_GATEWAY, "AI_003", "AI 응답을 처리할 수 없습니다."),
    AI_REQUEST_SERIALIZE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI_004", "AI 요청을 생성할 수 없습니다."),
    AI_PROMPT_LOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI_005", "AI 프롬프트를 읽을 수 없습니다."),

    // mofa
    PUBLIC_DATA_CALL_FAILED(HttpStatus.BAD_GATEWAY, "PUBLIC_DATA_001", "공공데이터 API 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;


}
