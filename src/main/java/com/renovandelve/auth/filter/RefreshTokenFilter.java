package com.renovandelve.auth.filter;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.renovandelve.auth.exception.RefreshTokenException;
import com.renovandelve.auth.exception.enums.ErrorCase;
import com.renovandelve.auth.handler.LoginSuccessHandler;
import com.renovandelve.member.repository.MemberRepository;
import com.renovandelve.auth.service.redis.SecurityTokenService;
import com.renovandelve.auth.util.JwtUtil;
import com.renovandelve.auth.util.RefreshCookieFactory;
import com.renovandelve.auth.util.TokenClaims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Map;

/**
 * AccessToken 재발급 필터.
 *
 * 보안상 지키는 것:
 * - 토큰 값은 절대 로그에 남기지 않는다 (로그 유출 = 세션 탈취)
 * - RefreshToken은 httpOnly 쿠키로만 오가며, 응답 본문에는 싣지 않는다
 * - typ=refresh 인 토큰만 재발급에 사용할 수 있다
 */
@Slf4j
public class RefreshTokenFilter extends OncePerRequestFilter {
    private final String refreshPath;
    private final JwtUtil jwtUtil;
    private final SecurityTokenService securityTokenService; // 레디스
    private final MemberRepository memberRepository;
    private final RefreshCookieFactory refreshCookieFactory;

    public RefreshTokenFilter(String refreshPath, JwtUtil jwtUtil,
                              SecurityTokenService securityTokenService,
                              MemberRepository memberRepository,
                              RefreshCookieFactory refreshCookieFactory) {
        this.refreshPath = refreshPath;
        this.jwtUtil = jwtUtil;
        this.securityTokenService = securityTokenService;
        this.memberRepository = memberRepository;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    /**
     * /api/auth/refresh 요청만 처리한다. RefreshToken 쿠키를 검증하고 Redis 값과 대조한 뒤,
     * 계정 상태·권한을 DB에서 다시 확인해 새 AccessToken을 발급한다. 다른 경로는 그대로 통과시킨다.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 요청경로 refreshPath 아니면 Pass
        if (!refreshPath.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 본문의 AccessToken은 선택 사항 (새로고침 직후에는 메모리 토큰이 없다)
        Map<String, String> tokens = parseRequestJson(request);
        String accessToken = tokens == null ? null : tokens.get("accessToken");

        // AccessToken 검증
        try {
            checkAccessToken(accessToken);
        } catch (RefreshTokenException e) {
            e.sendResponseError(response);
            return;
        }

        // 쿠키에 RefreshToken이 아예 없으면 (만료/삭제) 재로그인 필요
        String refreshToken = readRefreshCookie(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            new RefreshTokenException(ErrorCase.BAD_REFRESH).sendResponseError(response);
            return;
        }

        Map<String, Object> refreshClaims;
        try {
            refreshClaims = checkRefreshToken(refreshToken);
        } catch (RefreshTokenException e) {
            e.sendResponseError(response);
            return;
        }

        // 쿠키로 들어가있는 리프레시
        String loginId = (String) refreshClaims.get(TokenClaims.LOGIN_ID);
        // 레디스에 저장된 리프레시
        String redisRefresh = securityTokenService.getRefreshToken(loginId);

        if (redisRefresh == null || !constantTimeEquals(redisRefresh, refreshToken)) {
            // 서명은 유효한데 서버 보관분과 다르다 = 이미 폐기/교체된 토큰의 재사용 (탈취 의심)
            log.warn("RefreshToken 불일치 - 폐기된 토큰 재사용 의심 loginId={}", loginId);
            new RefreshTokenException(ErrorCase.BAD_REFRESH).sendResponseError(response);
            return;
        }

        // JWT 라이브러리는 exp를 Integer로 돌려줄 수 있음 -> (long) 직접 캐스팅 시 ClassCastException으로
        // 토큰 재발급 전체가 500으로 실패하던 버그. Number로 안전하게 변환한다.
        long exp = ((Number) refreshClaims.get("exp")).longValue();
        long gapTime = new Date(exp * 1000).getTime() - System.currentTimeMillis();

        // 계정 상태/권한을 매 재발급 시점에 다시 확인
        // -> 운영자가 계정을 정지시키면 다음 재발급부터 즉시 차단되고, 권한 변경도 반영된다.
        var member = memberRepository.findByLoginId(loginId).orElse(null);
        if (member == null || member.getStatus() == null || !member.getStatus().canLogin()) {
            log.info("정지/탈퇴 계정의 토큰 재발급 차단 loginId={}", loginId);
            securityTokenService.logout(loginId);
            new RefreshTokenException(ErrorCase.BAD_REFRESH).sendResponseError(response);
            return;
        }
        String role = member.getRole().name();

        String accessTokenValue = jwtUtil.generateToken(
                TokenClaims.access(loginId, role), LoginSuccessHandler.ACCESS_TOKEN_MINUTES);

        // 리프레시 12시간도 안남았으면 재발급 (슬라이딩 만료)
        long reissuancePeriod = 1000L * 60 * 60 * 12;
        if (gapTime < reissuancePeriod) {
            String newRefresh = jwtUtil.generateToken(
                    TokenClaims.refresh(loginId, role), LoginSuccessHandler.REFRESH_TOKEN_MINUTES);

            // 재발급 리프레시 새로운 값으로 다시 추가 (이전 값은 이 시점에 무효가 된다)
            securityTokenService.saveRefreshToken(loginId, newRefresh, LoginSuccessHandler.REFRESH_TOKEN_MINUTES);
            response.setHeader(HttpHeaders.SET_COOKIE,
                    refreshCookieFactory.issue(newRefresh, LoginSuccessHandler.REFRESH_COOKIE_SECONDS));
        }

        sendToken(accessTokenValue, response);
    }

    /**
     * 요청에서 RefreshToken 쿠키 값을 꺼낸다.
     *
     * @param request 요청
     * @return 토큰, 없으면 null
     */
    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (RefreshCookieFactory.NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** 비교 시간으로 토큰 내용을 추측하지 못하도록 상수 시간 비교 */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * AccessToken 검증
     * @param accessToken AccessToken
     */
    private void checkAccessToken(String accessToken) {
        if (accessToken == null) {
            return; // 메모리 보관이라 새로고침하면 사라진다 -> 없는 것은 정상
        }
        try {
            jwtUtil.validateToken(accessToken); // 검증
        } catch (ExpiredJwtException e) {
            // 만료된 AccessToken을 들고 재발급을 요청하는 것이 정상 흐름
        } catch (JwtException | IllegalArgumentException e) {
            log.info("재발급 요청의 AccessToken이 유효하지 않음: {}", e.getClass().getSimpleName());
            throw new RefreshTokenException(ErrorCase.BAD_ACCESS); // 형식 오류 / 서명 위조
        }
    }

    /**
     * RefreshToken 검증
     * @param refreshToken RefreshToken
     * @return PayLoad
     * @throws RefreshTokenException 토큰 검증 실패시 예외
     */
    private Map<String, Object> checkRefreshToken(String refreshToken) throws RefreshTokenException {
        Map<String, Object> claims;
        try {
            claims = jwtUtil.validateToken(refreshToken); // 검증
        } catch (ExpiredJwtException e) {
            throw new RefreshTokenException(ErrorCase.OLD_REFRESH); // 만료
        } catch (JwtException | IllegalArgumentException e) {
            log.info("RefreshToken이 유효하지 않음: {}", e.getClass().getSimpleName());
            throw new RefreshTokenException(ErrorCase.BAD_REFRESH); // 형식 오류 / 서명 위조
        }
        // AccessToken을 쿠키에 넣어 재발급을 받으려는 시도 차단
        if (!TokenClaims.isRefresh(claims)) {
            throw new RefreshTokenException(ErrorCase.BAD_REFRESH);
        }
        return claims;
    }

    /**
     * AccessToken 응답. RefreshToken은 httpOnly 쿠키로만 전달하고 본문에 싣지 않는다.
     * (본문에 실으면 스크립트가 읽을 수 있어 httpOnly의 의미가 사라진다)
     */
    private void sendToken(String accessTokenValue, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().println(new Gson().toJson(Map.of("accessToken", accessTokenValue)));
    }

    /**
     * Json 직렬화 역직렬화
     * @param request 요청값
     * @return Json <-> Map (본문이 없거나 잘못된 형식이면 null)
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseRequestJson(HttpServletRequest request) {
        try (Reader reader = new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Map.class);
        } catch (IOException | JsonSyntaxException | ClassCastException e) {
            log.info("재발급 요청 본문 파싱 실패: {}", e.getClass().getSimpleName());
        }
        return null;
    }
}
