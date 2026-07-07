package com.yaho.factchecker.global.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// 해시 유틸 — 코퍼스 문서 중복 판단용 content 해시 계산
public final class HashUtils {

    private HashUtils() {}

    /**
     * 문자열의 SHA-256 해시를 소문자 16진수 문자열(64자)로 반환
     * content_cleaned의 동일성 판단에 사용
     */
    public static String sha256(String input) {
        if (input == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원", e);
        }
    }
}
