package com.yaho.factchecker.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class EmailService {
     //랜덤 secure  인스턴스 생성
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;


    @Value("${spring.mail.username}")
    private String fromEmail;
    // 메모리(프로퍼티) 기반 인증 코드 저장소
    private final Map<String, String> verificationCodes = new ConcurrentHashMap<>();

    // 진짜 이메일을 보내고 메모리에 코드를 저장하는 메서드
    public void sendVerificationEmail(String email) {
        // 6자리 난수 인증 코드 생성
        String verificationCode = String.format("%06d", SECURE_RANDOM.nextInt(1000000));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail); // 하드코딩제거 후 수정
        message.setTo(email.trim());
        message.setSubject("[팩트체커] 회원가입 이메일 인증 코드입니다.");
        message.setText("안녕하세요. 팩트체커 플랫폼입니다.\n\n" +
                "인증 번호 [ " + verificationCode + " ] 를 입력창에 정확히 입력해 주세요.");

        mailSender.send(message);

        // 메모리에 코드가 누적되도록 저장
        verificationCodes.put(email.trim(), verificationCode);
    }

    // 💾 메모리에서 코드를 꺼내와 검증하는 메서드
    public boolean verifyCode(String email, String code) {
        String savedCode = verificationCodes.get(email.trim());

        if (savedCode != null && savedCode.equals(code.trim())) {
            verificationCodes.remove(email.trim()); // 인증 성공 시 삭제
            return true;
        }
        return false;
    }
}