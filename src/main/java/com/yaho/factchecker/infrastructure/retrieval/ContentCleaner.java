package com.yaho.factchecker.infrastructure.retrieval;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/**
 * 공공데이터 포털 API 중에서 HTML 문서로 제공되는 경우, HTML 태그를 제거하고 평문으로 정제
 * - \r\n 및 연속 공백은 단일 공백으로 정규화
 */
@Component
public class ContentCleaner {

    public String clean(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }
        // jsoup.text() = 태그 제거 + 엔티티 디코딩 + 공백 정규화
        String text = Jsoup.parse(rawHtml).text();
        return text.replaceAll("\\s+", " ").trim();
    }
}
