package com.renovandelve.member.service;

import com.renovandelve.global.exception.BusinessException;
import com.renovandelve.global.mail.MailService;

import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.domain.memberentity.MemberStatus;
import com.renovandelve.member.repository.MemberRepository;
import com.renovandelve.member.service.redis.PasswordResetTokenStore;
import com.renovandelve.global.redis.RateLimitService;
import com.renovandelve.auth.service.redis.SecurityTokenService;
import com.renovandelve.auth.service.redis.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 아이디 찾기 / 비밀번호 재설정 규칙.
 * 메일 발송은 목으로 대체하고 "누구에게, 무엇을 보냈는가"만 본다 (실제 SMTP 전송은 MailServiceTest).
 */
class AccountRecoveryServiceTest {

    private static final String EMAIL = "hero@example.com";
    private static final String BASE_URL = "https://game.example.com";

    private MemberRepository memberRepository;
    private MailService mailService;
    private PasswordResetTokenStore tokenStore;
    private SecurityTokenService securityTokenService;
    private TokenRevocationService tokenRevocationService;
    private RateLimitService rateLimitService;
    private com.renovandelve.auth.handler.LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AccountRecoveryService service;

    private MemberEntity member;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        mailService = mock(MailService.class);
        tokenStore = mock(PasswordResetTokenStore.class);
        securityTokenService = mock(SecurityTokenService.class);
        tokenRevocationService = mock(TokenRevocationService.class);
        rateLimitService = mock(RateLimitService.class);
        loginAttemptService = mock(com.renovandelve.auth.handler.LoginAttemptService.class);
        service = new AccountRecoveryService(memberRepository, mailService, tokenStore, passwordEncoder,
                securityTokenService, tokenRevocationService, rateLimitService, loginAttemptService, BASE_URL + "/");

        member = MemberEntity.builder()
                .memberId(7L).loginId("hero01").name("김용사").email(EMAIL)
                .passwordHash(passwordEncoder.encode("oldPass123")).build();
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
        when(rateLimitService.retryAfterSeconds(anyString(), anyString(), anyInt(), any(Duration.class))).thenReturn(0L);
        when(mailService.isConfigured()).thenReturn(true);
    }

    /* ==================== 아이디 찾기 ==================== */

    @Test
    @DisplayName("아이디 찾기: 이름과 이메일이 맞으면 가입된 이메일로 아이디를 보낸다 (이메일 대소문자·공백 무시)")
    void findIdSendsLoginId() {
        service.sendLoginId(" 김용사 ", "  Hero@Example.COM ");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq(EMAIL), contains("아이디"), body.capture());
        assertThat(body.getValue()).contains("hero01");
    }

    @Test
    @DisplayName("아이디 찾기: 이름이 다르면 메일을 보내지 않고, 예외도 내지 않는다")
    void findIdWithWrongNameSendsNothing() {
        assertThatCode(() -> service.sendLoginId("박마법", EMAIL)).doesNotThrowAnyException();

        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("아이디 찾기: 없는 이메일이어도 맞는 경우와 똑같이 조용히 끝난다")
    void findIdWithUnknownEmailIsSilent() {
        when(memberRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatCode(() -> service.sendLoginId("김용사", "nobody@example.com")).doesNotThrowAnyException();

        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("아이디 찾기: 탈퇴한 계정은 찾지 못한다")
    void findIdIgnoresWithdrawnMember() {
        member.withdraw();
        // 탈퇴하면 이메일이 지워지지만, 남아 있는 경우에도 보내지 않아야 한다
        MemberEntity withdrawn = MemberEntity.builder().memberId(8L).loginId("withdrawn_8").name("김용사")
                .email(EMAIL).status(MemberStatus.WITHDRAWN).build();
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(withdrawn));

        service.sendLoginId("김용사", EMAIL);

        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("같은 주소로의 발송 한도를 넘으면 보내지 않는다 (응답은 그대로)")
    void mailToSameAddressIsThrottled() {
        when(rateLimitService.retryAfterSeconds(eq("mail"), anyString(), anyInt(), any(Duration.class))).thenReturn(1800L);

        assertThatCode(() -> service.sendLoginId("김용사", EMAIL)).doesNotThrowAnyException();
        assertThatCode(() -> service.requestPasswordReset("hero01", EMAIL)).doesNotThrowAnyException();

        verify(mailService, never()).send(anyString(), anyString(), anyString());
        verify(tokenStore, never()).issue(anyLong());
    }

    @Test
    @DisplayName("요청 제한 키에 이메일 주소가 그대로 들어가지 않는다")
    void rateLimitKeyDoesNotContainEmail() {
        service.sendLoginId("김용사", EMAIL);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(rateLimitService).retryAfterSeconds(eq("mail"), subject.capture(), anyInt(), any(Duration.class));
        assertThat(subject.getValue()).doesNotContain("hero").doesNotContain("@");
    }

    /* ==================== 비밀번호 재설정 요청 ==================== */

    @Test
    @DisplayName("재설정 요청: 아이디와 이메일이 맞으면 설정된 주소로 만든 1회용 링크를 보낸다")
    void resetRequestSendsLink() {
        when(tokenStore.issue(7L)).thenReturn("TOKEN123");

        service.requestPasswordReset("hero01", EMAIL);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq(EMAIL), contains("재설정"), body.capture());
        // 토큰은 # 뒤에 둔다 (서버 접속 로그에 남지 않게). 기준 주소 끝의 /는 겹치지 않는다
        assertThat(body.getValue()).contains(BASE_URL + "/reset-password#token=TOKEN123");
        assertThat(body.getValue()).doesNotContain("oldPass123"); // 비밀번호를 메일로 보내지 않는다
    }

    @Test
    @DisplayName("재설정 요청: 아이디가 다르면 토큰도 메일도 만들지 않는다")
    void resetRequestWithWrongLoginId() {
        assertThatCode(() -> service.requestPasswordReset("someone", EMAIL)).doesNotThrowAnyException();

        verify(tokenStore, never()).issue(anyLong());
        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("재설정 요청: 비밀번호가 없는 소셜 계정에는 링크를 보내지 않는다")
    void resetRequestForSocialAccount() {
        MemberEntity social = MemberEntity.builder().memberId(9L).loginId("social01").email(EMAIL).build();
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(social));

        service.requestPasswordReset("social01", EMAIL);

        verify(tokenStore, never()).issue(anyLong());
        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("재설정 요청: SMTP가 설정되지 않았으면 보낼 수 없는 토큰을 만들지 않는다")
    void resetRequestWithoutSmtpIssuesNoToken() {
        when(mailService.isConfigured()).thenReturn(false);

        assertThatCode(() -> service.requestPasswordReset("hero01", EMAIL)).doesNotThrowAnyException();

        verify(tokenStore, never()).issue(anyLong());
        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("재설정 요청: 내부 오류가 나도 응답이 달라지지 않도록 예외를 삼킨다")
    void resetRequestSwallowsFailures() {
        when(tokenStore.issue(7L)).thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> service.requestPasswordReset("hero01", EMAIL)).doesNotThrowAnyException();
        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    /* ==================== 재설정 확정 ==================== */

    @Test
    @DisplayName("재설정: 비밀번호가 바뀌고 RefreshToken 삭제와 AccessToken 폐기가 함께 일어난다")
    void resetChangesPasswordAndKillsSessions() {
        when(tokenStore.consume("TOKEN123")).thenReturn(7L);

        service.resetPassword("TOKEN123", "newPass456");

        assertThat(passwordEncoder.matches("newPass456", member.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("oldPass123", member.getPasswordHash())).isFalse();
        verify(memberRepository).save(member);
        verify(securityTokenService).logout("hero01");
        verify(tokenRevocationService).revoke("hero01");
        verify(loginAttemptService).clearAccount("hero01"); // 잠긴 계정도 새 비밀번호로 바로 들어온다
        verify(mailService).send(eq(EMAIL), contains("변경"), anyString()); // 본인에게 변경 사실을 알린다
    }

    @Test
    @DisplayName("재설정: 없는·만료된·이미 쓴 토큰이면 거부하고 아무것도 바꾸지 않는다")
    void resetWithInvalidTokenIsRejected() {
        when(tokenStore.consume("USED")).thenReturn(null);
        String before = member.getPasswordHash();

        assertThatThrownBy(() -> service.resetPassword("USED", "newPass456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("만료");

        assertThat(member.getPasswordHash()).isEqualTo(before);
        verify(memberRepository, never()).save(any());
        verifyNoInteractions(securityTokenService, tokenRevocationService);
        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("재설정: 링크를 받은 뒤 탈퇴한 계정은 재설정할 수 없다")
    void resetForWithdrawnMemberIsRejected() {
        when(tokenStore.consume("TOKEN123")).thenReturn(7L);
        member.withdraw();

        assertThatThrownBy(() -> service.resetPassword("TOKEN123", "newPass456"))
                .isInstanceOf(BusinessException.class);
        verify(memberRepository, never()).save(any());
    }
}
