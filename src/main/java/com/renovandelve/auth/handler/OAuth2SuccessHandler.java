package com.renovandelve.auth.handler;

import com.google.gson.Gson;
import com.renovandelve.auth.service.redis.SecurityTokenService;
import com.renovandelve.auth.util.JwtUtil;
import com.renovandelve.auth.util.RefreshCookieFactory;
import com.renovandelve.auth.util.TokenClaims;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtUtil jwtUtil;
    private final SecurityTokenService securityTokenService;
    private final RefreshCookieFactory refreshCookieFactory;

    public OAuth2SuccessHandler(JwtUtil jwtUtil, SecurityTokenService securityTokenService,
                                RefreshCookieFactory refreshCookieFactory) {
        this.jwtUtil = jwtUtil;
        this.securityTokenService = securityTokenService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    /**
     * 구글 로그인 성공 시 토큰을 발급한다 (미완성 기능: 소셜 회원은 loginId가 없다).
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String socialId = (String) oAuth2User.getAttribute("sub");

        // AccessToken 발급
        String accessToken = jwtUtil.generateToken(
                TokenClaims.access(socialId, "USER"), LoginSuccessHandler.ACCESS_TOKEN_MINUTES);

        // RefreshToken 발급
        String refreshToken = jwtUtil.generateToken(
                TokenClaims.refresh(socialId, "USER"), LoginSuccessHandler.REFRESH_TOKEN_MINUTES);

        // Redis 저장
        securityTokenService.saveRefreshToken(socialId, refreshToken, LoginSuccessHandler.REFRESH_TOKEN_MINUTES);

        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookieFactory.issue(refreshToken, LoginSuccessHandler.REFRESH_COOKIE_SECONDS));

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().println(new Gson().toJson(Map.of("accessToken", accessToken)));
    }
}
