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
    @DisplayName("SMTP로 메일이 전송된다: 받는 사람, 보내는 사람, 제목, 텍스트 + HTML 본문(한글과 링크)")
    void sendsMailOverSmtp() throws Exception {
        MailService mailService = new MailService(senderTo(greenMail.getSmtp().getPort()),
                "sender", "smtp.example.com", "sender@example.com");
        String link = "http://localhost:5173/reset-password#token=abc_DEF-123";

        mailService.send("hero@example.com", MailTemplates.passwordReset(link, 30));

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("hero@example.com");
        assertThat(received[0].getFrom()[0].toString()).isEqualTo("sender@example.com");
        assertThat(received[0].getSubject()).isEqualTo("[Renova & Delve] 비밀번호 재설정 안내");
        String raw = GreenMailUtil.getWholeMessage(received[0]);
        assertThat(raw).contains("multipart/alternative").contains("text/plain").contains("text/html");
        assertThat(GreenMailUtil.getHeaders(received[0])).contains("UTF-8");

        String text = partOf(received[0], "text/plain");
        String html = partOf(received[0], "text/html");
        assertThat(text).contains("새 비밀번호를 정해주세요").contains(link);
        assertThat(html).contains("<h1").contains("href=\"" + link + "\"");
    }

    /** 여러 겹의 multipart 안에서 처음 나오는 해당 형식의 본문 */
    private static String partOf(jakarta.mail.Part part, String type) throws Exception {
        if (part.isMimeType(type)) return part.getContent().toString();
        if (part.getContent() instanceof jakarta.mail.Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = partOf(multipart.getBodyPart(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    @DisplayName("SMTP 계정이 설정되지 않았으면 발송을 시도하지 않는다 (서버는 그대로 동작)")
    void skipsWhenNotConfigured() {
        MailService mailService = new MailService(senderTo(greenMail.getSmtp().getPort()), "", "smtp.naver.com", "");

        assertThat(mailService.isConfigured()).isFalse();
        assertThatCode(() -> mailService.send("hero@example.com", new MailContent("제목", "본문", "<p>본문</p>"))).doesNotThrowAnyException();
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    @DisplayName("SMTP 서버에 붙지 못해도 예외가 밖으로 나가지 않는다 (요청 처리는 계속된다)")
    void connectionFailureDoesNotPropagate() {
        // 아무도 듣고 있지 않은 포트
        MailService mailService = new MailService(senderTo(1), "sender", "smtp.example.com", "sender@example.com");
        mailService.RETRY_DELAYS_MS = new long[]{1, 1};

        assertThatCode(() -> mailService.send("hero@example.com", new MailContent("제목", "본문", "<p>본문</p>"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("일시적인 SMTP 오류는 다시 시도해 결국 보낸다")
    void retriesTransientFailure() {
        org.springframework.mail.javamail.JavaMailSender sender = org.mockito.Mockito.mock(org.springframework.mail.javamail.JavaMailSender.class);
        org.mockito.Mockito.when(sender.createMimeMessage())
                .thenReturn(new MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("일시 오류"))
                .doNothing()
                .when(sender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
        MailService mailService = new MailService(sender, "sender", "smtp.example.com", "sender@example.com");
        mailService.RETRY_DELAYS_MS = new long[]{1, 1};

        mailService.send("hero@example.com", new MailContent("제목", "본문", "<p>본문</p>"));

        org.mockito.Mockito.verify(sender, org.mockito.Mockito.times(2))
                .send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
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
