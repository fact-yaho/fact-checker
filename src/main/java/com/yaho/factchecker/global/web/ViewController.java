package com.yaho.factchecker.global.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 프론트 신규 뷰 라우트.
 * 기존 UserViewController 가 /login, /signup, /mypage 를 담당하므로
 * 여기서는 나머지 3개(/, /result, /history)만 매핑합니다.
 *
 * ⚠️ 패키지 경로는 프로젝트 구조에 맞게 조정하세요.
 *    (com.yaho.factchecker 하위면 컴포넌트 스캔에 잡힙니다.)
 * ⚠️ 원한다면 이 3줄을 기존 UserViewController 에 그대로 옮겨도 됩니다.
 */
@Controller
public class ViewController {

    // 홈 / 팩트체크 입력
    @GetMapping("/")
    public String home() {
        return "index";
    }

    // 검증 보고서 (?id=analysisResultId)
    @GetMapping("/result")
    public String result() {
        return "result";
    }

    // 검증 기록 (로그인 필요 — 데이터는 JS가 fetch)
    @GetMapping("/history")
    public String history() {
        return "history";
    }
}
