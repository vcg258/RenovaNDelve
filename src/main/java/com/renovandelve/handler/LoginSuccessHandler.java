package com.renovandelve.handler;

import com.google.gson.Gson;
import com.renovandelve.domain.MemberEntity;
import com.renovandelve.repository.MemberRepository;
import com.renovandelve.service.redis.SecurityTokenService;
import com.renovandelve.util.JwtUtil;
import com.renovandelve.util.RefreshCookieFactory;
import com.renovandelve.util.TokenClaims;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final JwtUtil jwtUtil;
    private final SecurityTokenService securityTokenService;
    private final MemberRepository memberRepository;
    private final LoginAttemptService loginAttemptService;
    private final RefreshCookieFactory refreshCookieFactory;

    public static final long ACCESS_TOKEN_MINUTES = 30;
    public static final long REFRESH_TOKEN_MINUTES = 60 * 24;   // 1일
    public static final long REFRESH_COOKIE_SECONDS = 60 * 60 * 24;

    public LoginSuccessHandler(JwtUtil jwtUtil, SecurityTokenService securityTokenService,
                               MemberRepository memberRepository, LoginAttemptService loginAttemptService,
                               RefreshCookieFactory refreshCookieFactory) {
        this.jwtUtil = jwtUtil;
        this.securityTokenService = securityTokenService;
        this.memberRepository = memberRepository;
        this.loginAttemptService = loginAttemptService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    /**
     * 로그인 성공 시 실패 횟수를 지우고, AccessToken은 본문으로, RefreshToken은 httpOnly 쿠키로 내려준다.
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String loginId = authentication.getName();
        log.info("로그인 성공 loginId={}", loginId);

        loginAttemptService.loginSucceeded((String) request.getAttribute(LoginAttemptService.ATTEMPT_KEY_ATTRIBUTE));

        MemberEntity member = memberRepository.findByLoginId(loginId).orElse(null);
        String role = member != null ? member.getRole().name() : "USER";
        if (member != null) {
            member.recordLogin();
            memberRepository.save(member);
        }

        // JWT 클레임에 권한을 포함 -> 인가 판정을 DB 조회 없이 토큰만으로 수행
        // typ 클레임으로 용도를 구분해 RefreshToken이 AccessToken으로 쓰이는 것을 막는다
        String accessToken = jwtUtil.generateToken(TokenClaims.access(loginId, role), ACCESS_TOKEN_MINUTES);
        String refreshToken = jwtUtil.generateToken(TokenClaims.refresh(loginId, role), REFRESH_TOKEN_MINUTES);
        securityTokenService.saveRefreshToken(loginId, refreshToken, REFRESH_TOKEN_MINUTES);

        // 경로 "/"로 발급되던 이전 쿠키를 지우고 새 쿠키(/api/auth 한정)를 내려준다
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieFactory.expireLegacy());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieFactory.issue(refreshToken, REFRESH_COOKIE_SECONDS));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", accessToken);
        body.put("loginId", loginId);
        body.put("nickname", member != null ? member.displayName() : loginId);
        body.put("role", role);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().println(new Gson().toJson(body));
    }
}
