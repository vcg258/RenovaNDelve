package com.renovandelve.filter;

import com.google.gson.Gson;
import com.renovandelve.exception.RefreshTokenException;
import com.renovandelve.exception.enums.ErrorCase;
import com.renovandelve.service.redis.SecurityTokenService;
import com.renovandelve.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Log4j2
public class RefreshTokenFilter extends OncePerRequestFilter {
    private final String refreshPath;
    private final JwtUtil jwtUtil;
    private final SecurityTokenService securityTokenService; // 레디스

    public RefreshTokenFilter(String refreshPath, JwtUtil jwtUtil, @Qualifier("securityRedisTemplate") SecurityTokenService securityTokenService) {
        this.refreshPath = refreshPath;
        this.jwtUtil = jwtUtil;
        this.securityTokenService = securityTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 요청경로 refreshPath 아니면 Pass
        if (!path.startsWith(refreshPath)) {
            log.info("skip Refreshing token...");
            filterChain.doFilter(request, response);
            return;
        }

        String refreshToken = null;
        if (request.getCookies() != null) {
            Cookie[] cookies = request.getCookies();
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("refreshToken")) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        log.info("Refreshing Token...");
        Map<String, String> tokens = parseRequestJson(request);
        if (tokens == null) {
            new RefreshTokenException(ErrorCase.BAD_ACCESS).sendResponseError(response);
            return;
        }
        String accessToken = tokens.get("accessToken");

        log.info("AccessToken : {}", accessToken);
        log.info("RefreshToken : {}", refreshToken);

        // AccessToken 검증
        try {
            checkAccessToken(accessToken);
        } catch (RefreshTokenException e) {
            e.sendResponseError(response);
            return;
        }

        // 쿠키에 RefreshToken이 아예 없으면 (만료/삭제) 재로그인 필요
        if (refreshToken == null || refreshToken.isBlank()) {
            log.info("RefreshToken 쿠키 없음 - 재로그인 필요");
            new RefreshTokenException(ErrorCase.BAD_REFRESH).sendResponseError(response);
            return;
        }

        Map<String, Object> refreshClaims = null;
        try {
            refreshClaims = checkRefreshToken(refreshToken);
            log.info("RefreshToken 전달 확인용 : {}", refreshClaims);
        } catch (RefreshTokenException e) {
            e.sendResponseError(response);
            return;
        }

        // 쿠키로 들어가있는 리프레시
        String loginId = (String) refreshClaims.get("loginId");
        // 레디스에 저장된 리프레시
        String RedisRefresh = securityTokenService.getRefreshToken(loginId);

        if (RedisRefresh == null || !RedisRefresh.equals(refreshToken)) {
            log.error("refreshToken이 DB와 일치 하지 않음 (탈취)");
            new RefreshTokenException(ErrorCase.BAD_REFRESH).sendResponseError(response);
            return;
        }

        // JWT 라이브러리는 exp를 Integer로 돌려줄 수 있음 -> (long) 직접 캐스팅 시 ClassCastException으로
        // 토큰 재발급 전체가 500으로 실패하던 버그. Number로 안전하게 변환한다.
        long exp = ((Number) refreshClaims.get("exp")).longValue();
        Date expiredTime = new Date(exp * 1000);
        Date currentTime = new Date(System.currentTimeMillis());

        long gapTime = (long) (expiredTime.getTime() - currentTime.getTime());
        log.info("-------------------------------------------");
        log.info("currentTime: {}", currentTime);
        log.info("expiredTime: {}", expiredTime);
        log.info("gapTime: {}", gapTime);

        String accessTokenValue = jwtUtil.generateToken(Map.of("loginId", loginId), 30);

        // 리프레시 12시간도 안남았으면 재발급
        long reissuancePeriod = 1000 * 60 * 60 * 12;
        if (gapTime < reissuancePeriod) {
            long expiredToken = 60 * 24; // TODO 하루만 지정 (분단위)
            long expiredCookie = 60 * 60 * 24; // 위와 동일 (쿠키는 초단위)

            String newRefresh = jwtUtil.generateToken(Map.of("loginId", loginId), expiredToken);

            // 재발급 리프레시 새로운 값으로 다시 추가
            securityTokenService.saveRefreshToken(loginId, newRefresh, expiredToken);

            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", newRefresh)
                    .httpOnly(true)
                    .secure(false)
                    .path("/")
                    .maxAge(expiredCookie)
                    .sameSite("Strict")
                    .build();

            response.setHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        }
        log.info("Refresh Token result");
        log.info("accessToken : {}", accessTokenValue);

        sendToken(accessTokenValue, refreshToken, response);
    }

    /**
     * AccessToken 검증
     * @param accessToken AccessToken
     */
    private void checkAccessToken(String accessToken) {
        if (accessToken == null) {
            log.info("null은 재접속 통과"); // ViewData에 들어가있기 때문에 새로고침하면 날아감 그래서 Pass
            return;
        }
        try {
            jwtUtil.validateToken(accessToken); // 검증
        } catch (ExpiredJwtException e) {
            log.info("AccessToken Expired"); // 만료
        } catch (MalformedJwtException e) {
            log.info("Malformed AccessToken");
            throw new RefreshTokenException(ErrorCase.BAD_ACCESS); // 토큰 형식 잘못됨
        } catch (SignatureException e) {
            log.info("BadSignatured AccessToken");
            throw new RefreshTokenException(ErrorCase.BAD_ACCESS); // 서명 위조
        }
    }

    /**
     * RefreshToken 검증
     * @param refreshToken RefreshToken
     * @return PayLoad
     * @throws RefreshTokenException 토큰 검증 실패시 예외
     */
    private Map<String, Object> checkRefreshToken(String refreshToken) throws RefreshTokenException {
        try {
            return jwtUtil.validateToken(refreshToken); // 검증
        } catch (ExpiredJwtException e) {
            log.info("RefreshToken Exception"); // 만료
            throw new RefreshTokenException(ErrorCase.OLD_REFRESH); // 만료
        } catch (MalformedJwtException e) {
            log.info("RefreshToken Malformed");
            throw new RefreshTokenException(ErrorCase.BAD_REFRESH); // 형식 잘못됨
        } catch (SignatureException e) {
            log.info("RefreshToken BadSignatured");
            throw new RefreshTokenException(ErrorCase.BAD_REFRESH); // 서명 위조
        }
    }

    /**
     * 토큰 발권
     * @param accessTokenValue AccessToken 값
     * @param refreshTokenValue RefreshToken 값
     * @param response 응답
     * @throws IOException 잘못된 형식의 값이 들어갈때 예외
     */
    private void sendToken(String accessTokenValue, String refreshTokenValue, HttpServletResponse response) throws IOException {
        Map<String, Object> keyMap = Map.of("accessToken", accessTokenValue, "refreshToken", refreshTokenValue);
        Gson gson = new Gson();
        String jsonStr = gson.toJson(keyMap);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().println(jsonStr);
    }

    /**
     * Json 직렬화 역직렬화
     * @param request 요청값
     * @return Json <-> Map
     */
    private Map<String, String> parseRequestJson(HttpServletRequest request) {
        try (Reader reader = new InputStreamReader(request.getInputStream())) {
            Gson gson = new Gson();
            return gson.fromJson(reader, Map.class);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        return null;
    }
}
