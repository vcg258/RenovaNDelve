package com.renovandelve.service;

import com.renovandelve.util.EmailNormalizer;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 메일 발송 (SMTP).
 *
 * - 계정 정보(MAIL_USERNAME / MAIL_PASSWORD)가 없으면 발송을 시도하지 않고 경고만 남긴다.
 *   SMTP 설정이 없어도 서버는 기동돼야 하고, 메일이 필요한 기능만 동작하지 않아야 한다.
 * - 발송은 비동기다. SMTP 응답을 기다리면 요청이 느려질 뿐 아니라,
 *   "메일을 보낸 경우에만 응답이 늦다"는 차이로 계정 존재 여부가 드러난다.
 * - 본문(재설정 링크 포함)과 받는 사람 주소 전체는 로그에 남기지 않는다.
 */
@Log4j2
@Service
public class MailService {
    private final JavaMailSender mailSender;
    private final String username;
    private final String from;

    public MailService(JavaMailSender mailSender,
                       @Value("${spring.mail.username:}") String username,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${renovaNDelve.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.username = username == null ? "" : username.trim();
        this.from = resolveFrom(from, this.username, host);
        if (!isConfigured()) {
            log.warn("[메일] SMTP 계정(MAIL_USERNAME / MAIL_PASSWORD)이 설정되지 않았습니다. "
                    + "아이디 찾기·비밀번호 찾기 메일은 발송되지 않습니다.");
        }
    }

    public boolean isConfigured() {
        return !username.isEmpty();
    }

    @Async
    public void send(String to, String subject, String text) {
        if (!isConfigured()) {
            log.warn("[메일] SMTP 미설정으로 발송 생략 to={} subject={}", EmailNormalizer.mask(to), subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("[메일] 발송 완료 to={} subject={}", EmailNormalizer.mask(to), subject);
        } catch (Exception e) {
            // 발송 실패가 요청 처리를 깨뜨리면 안 된다. 원인만 남긴다
            log.error("[메일] 발송 실패 to={} subject={} 원인={}", EmailNormalizer.mask(to), subject, e.getMessage());
        }
    }

    /**
     * 보내는 주소. 네이버 SMTP는 로그인한 계정의 주소로만 보낼 수 있다.
     * MAIL_FROM이 비어 있으면 계정에서 만든다: 주소 형태면 그대로, 아이디만 있으면 SMTP 호스트의 도메인을 붙인다.
     */
    static String resolveFrom(String from, String username, String host) {
        if (from != null && !from.isBlank()) return from.trim();
        if (username == null || username.isBlank()) return "";
        if (username.contains("@")) return username;
        String domain = host == null ? "" : host.trim().replaceFirst("^smtp\\.", "");
        return domain.isEmpty() ? username : username + "@" + domain;
    }
}
