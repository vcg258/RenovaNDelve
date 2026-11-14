package com.renovandelve.global.mail;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 메일이 실제 SMTP 프로토콜로 나가는지 확인한다.
 * 테스트 안에서 가짜 SMTP 서버(GreenMail)를 띄우므로 외부 계정이나 네트워크가 필요 없다.
 * 네이버 SMTP로의 실제 발송(TLS, 계정 인증)은 이 테스트가 확인하지 못한다.
 */
class MailServiceTest {

    @RegisterExtension
    static final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("sender@example.com", "sender", "secret"));

    private JavaMailSenderImpl senderTo(int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(port);
        sender.setUsername("sender");
        sender.setPassword("secret");
        sender.setDefaultEncoding("UTF-8");
        sender.getJavaMailProperties().put("mail.smtp.auth", "true");
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "2000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", "2000");
        return sender;
    }

    @Test
    @DisplayName("SMTP로 메일이 전송된다: 받는 사람, 보내는 사람, 제목, 본문(한글과 링크)")
    void sendsMailOverSmtp() throws Exception {
        MailService mailService = new MailService(senderTo(greenMail.getSmtp().getPort()),
                "sender", "smtp.example.com", "sender@example.com");
        String link = "http://localhost:5173/reset-password#token=abc_DEF-123";

        mailService.send("hero@example.com", "[Renova & Delve] 비밀번호 재설정 안내", "아래 주소를 열어주세요.\n\n" + link);

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("hero@example.com");
        assertThat(received[0].getFrom()[0].toString()).isEqualTo("sender@example.com");
        assertThat(received[0].getSubject()).isEqualTo("[Renova & Delve] 비밀번호 재설정 안내");
        String body = received[0].getContent().toString();
        assertThat(body).contains("아래 주소를 열어주세요.").contains(link);
        assertThat(GreenMailUtil.getHeaders(received[0])).contains("UTF-8");
    }

    @Test
    @DisplayName("SMTP 계정이 설정되지 않았으면 발송을 시도하지 않는다 (서버는 그대로 동작)")
    void skipsWhenNotConfigured() {
        MailService mailService = new MailService(senderTo(greenMail.getSmtp().getPort()), "", "smtp.naver.com", "");

        assertThat(mailService.isConfigured()).isFalse();
        assertThatCode(() -> mailService.send("hero@example.com", "제목", "본문")).doesNotThrowAnyException();
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    @DisplayName("SMTP 서버에 붙지 못해도 예외가 밖으로 나가지 않는다 (요청 처리는 계속된다)")
    void connectionFailureDoesNotPropagate() {
        // 아무도 듣고 있지 않은 포트
        MailService mailService = new MailService(senderTo(1), "sender", "smtp.example.com", "sender@example.com");

        assertThatCode(() -> mailService.send("hero@example.com", "제목", "본문")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("보내는 주소: MAIL_FROM이 없으면 계정에서 만든다 (네이버는 아이디 + @naver.com)")
    void resolvesFromAddress() {
        assertThat(MailService.resolveFrom("", "myid", "smtp.naver.com")).isEqualTo("myid@naver.com");
        assertThat(MailService.resolveFrom(null, "myid@naver.com", "smtp.naver.com")).isEqualTo("myid@naver.com");
        assertThat(MailService.resolveFrom("noreply@game.com", "myid", "smtp.naver.com")).isEqualTo("noreply@game.com");
        assertThat(MailService.resolveFrom("", "", "smtp.naver.com")).isEmpty();
    }
}
