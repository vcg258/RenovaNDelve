package com.renovandelve.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * RefreshToken 쿠키 생성 규칙을 한 곳에서 관리한다.
 * (기존에는 로그인/재발급/로그아웃/OAuth 4곳에 secure(false)가 각각 하드코딩돼 있었다)
 *
 * - httpOnly: 스크립트에서 읽을 수 없음 (XSS 탈취 방지)
 * - SameSite=Strict: 타 사이트발 요청에 쿠키가 실리지 않음 (CSRF 방지)
 * - secure: HTTPS 전용 여부. 로컬 개발은 false, 운영(prod 프로파일)은 true
 * - path: 재발급/로그아웃에만 쓰이므로 /api/auth 로 한정해 불필요한 전송을 줄인다
 */
@Component
public class RefreshCookieFactory {
    public static final String NAME = "refreshToken";
    private static final String PATH = "/api/auth";

    private final boolean secure;

    public RefreshCookieFactory(@Value("${renovaNDelve.cookie.secure:false}") boolean secure) {
        this.secure = secure;
    }

    public String issue(String refreshToken, long maxAgeSeconds) {
        return ResponseCookie.from(NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .path(PATH)
                .maxAge(maxAgeSeconds)
                .sameSite("Strict")
                .build()
                .toString();
    }

    public String expire() {
        return issue("", 0);
    }

    /**
     * 경로를 "/"로 발급하던 이전 버전 쿠키 제거용.
     * 경로가 다르면 다른 쿠키로 취급되므로, 로그아웃 시 구 쿠키도 함께 만료시킨다.
     */
    public String expireLegacy() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true).secure(secure).path("/").maxAge(0).sameSite("Strict")
                .build().toString();
    }
}
