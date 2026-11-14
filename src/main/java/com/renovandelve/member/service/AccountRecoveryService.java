package com.renovandelve.member.service;

import com.renovandelve.global.mail.MailService;

import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.domain.memberentity.MemberStatus;
import com.renovandelve.member.repository.MemberRepository;
import com.renovandelve.auth.handler.LoginAttemptService;
import com.renovandelve.member.service.redis.PasswordResetTokenStore;
import com.renovandelve.global.redis.RateLimitService;
import com.renovandelve.auth.service.redis.SecurityTokenService;
import com.renovandelve.auth.service.redis.TokenRevocationService;
import com.renovandelve.member.util.EmailNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

/**
 * 아이디 찾기 / 비밀번호 재설정.
 *
 * 지키는 선:
 * - 요청 API는 계정이 있든 없든 같은 응답을 준다. 이 서비스의 요청 메서드는 값을 돌려주지 않고 예외도 내지 않는다.
 *   (입력이 맞았는지는 메일을 받은 사람만 안다)
 * - 임시 비밀번호를 메일로 보내지 않는다. 1회용 링크로 본인이 새 비밀번호를 정한다.
 * - 링크의 주소는 설정값(APP_BASE_URL)으로 만든다. 요청의 Host 헤더를 쓰면 공격자가 자기 도메인으로 향하는 링크를 보내게 할 수 있다.
 * - 재설정이 끝나면 RefreshToken을 지우고 발급된 AccessToken을 폐기한다 (탈취된 세션 차단).
 */
@Slf4j
@Service
public class AccountRecoveryService {
    /** 같은 주소로 보내는 메일 수 제한 (남의 주소를 넣어 메일을 쏟아붓는 것 방지) */
    private static final int MAIL_LIMIT_PER_ADDRESS = 3;
    private static final Duration MAIL_LIMIT_WINDOW = Duration.ofHours(1);

    private final MemberRepository memberRepository;
    private final MailService mailService;
    private final PasswordResetTokenStore tokenStore;
    private final PasswordEncoder passwordEncoder;
    private final SecurityTokenService securityTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final RateLimitService rateLimitService;
    private final LoginAttemptService loginAttemptService;
    private final String baseUrl;

    public AccountRecoveryService(MemberRepository memberRepository, MailService mailService,
                                  PasswordResetTokenStore tokenStore, PasswordEncoder passwordEncoder,
                                  SecurityTokenService securityTokenService,
                                  TokenRevocationService tokenRevocationService,
                                  RateLimitService rateLimitService,
                                  LoginAttemptService loginAttemptService,
                                  @Value("${renovaNDelve.app.base-url:http://localhost:5173}") String baseUrl) {
        this.memberRepository = memberRepository;
        this.mailService = mailService;
        this.tokenStore = tokenStore;
        this.passwordEncoder = passwordEncoder;
        this.securityTokenService = securityTokenService;
        this.tokenRevocationService = tokenRevocationService;
        this.rateLimitService = rateLimitService;
        this.loginAttemptService = loginAttemptService;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** 아이디 찾기: 이름과 이메일이 모두 맞는 계정이 있으면 그 이메일로 아이디를 보낸다 */
    @Transactional(readOnly = true)
    public void sendLoginId(String name, String email) {
        try {
            String address = EmailNormalizer.normalize(email);
            Optional<MemberEntity> found = findRecoverable(address)
                    .filter(member -> sameName(member.getName(), name));
            if (found.isEmpty() || !mailAllowed(address)) return;

            MemberEntity member = found.get();
            mailService.send(address, "[Renova & Delve] 아이디 안내", """
                    %s 님, 요청하신 아이디를 알려드립니다.

                    아이디: %s

                    본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다. 계정에는 아무 변화가 없습니다.
                    """.formatted(member.getName(), member.getLoginId()));
        } catch (Exception e) {
            // 실패해도 응답은 달라지면 안 된다
            log.error("아이디 찾기 처리 실패: {}", e.getMessage());
        }
    }

    /** 비밀번호 찾기: 아이디와 이메일이 모두 맞는 계정이 있으면 1회용 재설정 링크를 보낸다 */
    @Transactional(readOnly = true)
    public void requestPasswordReset(String loginId, String email) {
        try {
            String address = EmailNormalizer.normalize(email);
            Optional<MemberEntity> found = findRecoverable(address)
                    .filter(member -> member.getLoginId().equals(loginId == null ? null : loginId.trim()))
                    // 소셜 로그인 계정은 비밀번호가 없다
                    .filter(member -> member.getPasswordHash() != null);
            if (found.isEmpty() || !mailAllowed(address)) return;
            if (!mailService.isConfigured()) {
                // 보낼 수 없는 링크의 토큰을 만들어 두지 않는다
                log.warn("[메일] SMTP 미설정으로 재설정 링크를 보내지 못했습니다. memberId={}", found.get().getMemberId());
                return;
            }

            String token = tokenStore.issue(found.get().getMemberId());
            // 토큰은 주소의 # 뒤(fragment)에 둔다. 서버로 전송되지 않아 접속 로그나 Referer에 남지 않는다
            String link = baseUrl + "/reset-password#token=" + token;
            mailService.send(address, "[Renova & Delve] 비밀번호 재설정 안내", """
                    비밀번호 재설정을 요청하셨습니다. 아래 주소를 열어 새 비밀번호를 정해주세요.

                    %s

                    - 이 주소는 %d분 동안, 한 번만 쓸 수 있습니다.
                    - 본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다. 비밀번호는 바뀌지 않습니다.
                    """.formatted(link, PasswordResetTokenStore.TTL.toMinutes()));
        } catch (Exception e) {
            log.error("비밀번호 재설정 요청 처리 실패: {}", e.getMessage());
        }
    }

    /**
     * 재설정 링크가 아직 유효한지 확인한다. 토큰을 소비하지 않는다.
     *
     * @param token 링크의 토큰
     * @return 유효하면 true
     */
    public boolean isResetTokenValid(String token) {
        return tokenStore.isValid(token);
    }

    /**
     * 비밀번호 재설정 확정. 토큰은 여기서 소비되며, 실패하든 성공하든 다시 쓸 수 없다.
     * @throws IllegalArgumentException 토큰이 없거나 만료됐거나 이미 쓰인 경우
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Long memberId = tokenStore.consume(token);
        MemberEntity member = memberId == null ? null : memberRepository.findById(memberId)
                .filter(found -> found.getStatus() != MemberStatus.WITHDRAWN)
                .orElse(null);
        if (member == null) {
            throw new IllegalArgumentException("재설정 링크가 만료되었거나 이미 사용되었습니다. 비밀번호 찾기를 다시 진행해주세요.");
        }

        member.changePassword(passwordEncoder.encode(newPassword));
        memberRepository.save(member);

        // 기존 세션 전부 차단: 재발급 수단(RefreshToken)과 이미 발급된 AccessToken
        securityTokenService.logout(member.getLoginId());
        tokenRevocationService.revoke(member.getLoginId());
        // 비밀번호를 잊어 잠긴 계정이 새 비밀번호로 바로 들어올 수 있게 잠금을 푼다
        loginAttemptService.clearAccount(member.getLoginId());
        log.info("비밀번호 재설정 완료 memberId={}", member.getMemberId());

        if (member.getEmail() != null) {
            mailService.send(member.getEmail(), "[Renova & Delve] 비밀번호가 변경되었습니다", """
                    계정(%s)의 비밀번호가 방금 재설정되었습니다. 모든 기기에서 로그아웃되었습니다.

                    본인이 한 일이 아니라면 곧바로 비밀번호 찾기로 다시 재설정하고, 이메일 계정의 비밀번호도 바꿔주세요.
                    """.formatted(member.getLoginId()));
        }
    }

    /** 이메일로 찾을 수 있는 계정: 탈퇴하지 않았고 일반 로그인 아이디가 있는 회원 */
    private Optional<MemberEntity> findRecoverable(String address) {
        if (address == null) return Optional.empty();
        return memberRepository.findByEmail(address)
                .filter(member -> member.getStatus() != MemberStatus.WITHDRAWN)
                .filter(member -> member.getLoginId() != null);
    }

    /**
     * 이름이 같은지 비교한다 (앞뒤 공백·대소문자 무시).
     *
     * @param stored 저장된 이름
     * @param given  입력한 이름
     * @return 같으면 true
     */
    private boolean sameName(String stored, String given) {
        return stored != null && given != null && stored.trim().equalsIgnoreCase(given.trim());
    }

    /**
     * 같은 주소로 보내는 메일이 한도 안인지 확인한다. Redis 키에는 주소 대신 해시를 쓴다.
     *
     * @param address 받는 주소
     * @return 보내도 되면 true
     */
    private boolean mailAllowed(String address) {
        // 주소 자체가 아니라 해시를 키로 쓴다 (Redis 키에 개인정보를 남기지 않는다)
        String subject = PasswordResetTokenStore.hashForKey(address);
        boolean allowed = rateLimitService.retryAfterSeconds("mail", subject, MAIL_LIMIT_PER_ADDRESS, MAIL_LIMIT_WINDOW) == 0;
        if (!allowed) {
            log.warn("같은 주소로의 메일 발송 한도 초과 to={}", EmailNormalizer.mask(address));
        }
        return allowed;
    }
}
