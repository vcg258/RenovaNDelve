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
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Map;

@Log4j2
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtUtil jwtUtil;
    private final SecurityTokenService securityTokenService;

    public OAuth2SuccessHandler(JwtUtil jwtUtil, @Qualifier("securityRedisTemplate") SecurityTokenService securityTokenService) {
        this.jwtUtil = jwtUtil;
        this.securityTokenService = securityTokenService;
    }


    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String socialId = (String) oAuth2User.getAttribute("sub");

        // AccessToken 발급
        String accessToken = jwtUtil.generateToken(Map.of("loginId", socialId), 30);

        // RefreshToken 발급
        long refreshExpiredMinutes = 60 * 24;
        String refreshToken = jwtUtil.generateToken(Map.of("loginId", socialId), refreshExpiredMinutes);

        // Redis 저장
        securityTokenService.saveRefreshToken(socialId, refreshToken, refreshExpiredMinutes);

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Strict")
                .maxAge(refreshExpiredMinutes * 60) // 초단위로 계산 되기 때문
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Gson gson = new Gson();
        response.getWriter().println(gson.toJson(Map.of("accessToken", accessToken)));
    }
}
