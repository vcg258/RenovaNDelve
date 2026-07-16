package com.renovandelve.handler;

import com.google.gson.Gson;
import com.renovandelve.service.redis.SecurityTokenService;
import com.renovandelve.util.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Map;

@Log4j2
public class LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final JwtUtil jwtUtil;
    private final SecurityTokenService securityTokenService;

    public LoginSuccessHandler(JwtUtil jwtUtil, @Qualifier("securityRedisTemplate") SecurityTokenService securityTokenService) {
        this.jwtUtil = jwtUtil;
        this.securityTokenService = securityTokenService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        log.info("Login Success Handler");

        log.info("로그인 성공한 사용자 정보 : {}", authentication);

        long expiredToken = 60 * 24; // TODO 하루만 지정 (분단위)
        long expiredCookie = 60 * 60 * 24; // 위와 동일 (쿠키는 초단위)

        // TODO 현재는 로그인 아이디만 넘겨줌 다른게 있을까?
        Map<String, Object> claim = Map.of(
                "loginId", authentication.getName()
        );

        log.info("만료시간 각각 지정 expiredToken: {}, expiredCookie: {}", expiredToken, expiredCookie);

        String accessToken = jwtUtil.generateToken(claim, 30);// TODO 엑세스 30분
        String refreshToken = jwtUtil.generateToken(claim, expiredToken);
        log.info("AccessToken: {}", accessToken);
        log.info("RefreshToken: {}", refreshToken);
        securityTokenService.saveRefreshToken(authentication.getName(), refreshToken, expiredToken); // 레디스 저장

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true) // XSS 방지
                .path("/") // 전역 설정
                .maxAge(expiredCookie) // 쿠키 기간
                .secure(false) // HTTPS가 아님 false
                .sameSite("Strict") // CSRF 방지
                .build();

        response.setHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        Map<String, Object> keyMap = Map.of(
                "accessToken", accessToken
        );

        Gson gson = new Gson();
        String jsonStr = gson.toJson(keyMap);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().println(jsonStr);
    }
}
