package com.renovandelve.global.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailTemplatesTest {

    @Test
    @DisplayName("틀의 자리표시자가 모두 채워진다")
    void noPlaceholderLeft() {
        for (MailContent mail : new MailContent[]{
                MailTemplates.loginId("김용사", "hero01", "http://localhost:5173/auth"),
                MailTemplates.passwordReset("http://localhost:5173/reset-password#token=T", 30),
                MailTemplates.passwordChanged("hero01", "http://localhost:5173/recover")}) {
            assertThat(mail.html()).doesNotContain("{{").startsWith("<!doctype html>");
            assertThat(mail.subject()).startsWith("[Renova & Delve] ");
            assertThat(mail.text()).isNotBlank().doesNotContain("<");
        }
    }

    @Test
    @DisplayName("사용자가 입력한 이름·아이디는 HTML로 이스케이프된다")
    void userInputIsEscaped() {
        MailContent mail = MailTemplates.loginId("<script>alert(1)</script>", "a\"b", "http://localhost:5173/auth");

        assertThat(mail.html()).doesNotContain("<script>").contains("&lt;script&gt;").contains("a&quot;b");
        assertThat(mail.text()).contains("<script>alert(1)</script>"); // 텍스트 본문은 그대로 (HTML로 해석되지 않는다)
    }

    @Test
    @DisplayName("재설정 메일에는 버튼과, 버튼이 안 열릴 때 쓸 주소 원문이 함께 들어간다")
    void resetHasButtonAndFallback() {
        String link = "http://localhost:5173/reset-password#token=abc";
        MailContent mail = MailTemplates.passwordReset(link, 30);

        assertThat(mail.html()).contains("새 비밀번호 정하기").contains("30분");
        assertThat(mail.html().split(java.util.regex.Pattern.quote("href=\"" + link + "\""), -1)).hasSize(3);
        assertThat(mail.text()).contains(link).contains("30분");
    }
}
