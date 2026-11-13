package com.renovandelve.util;

import java.util.Map;

/**
 * JWT 클레임 구성 규칙.
 *
 * AccessToken과 RefreshToken은 같은 키로 서명되므로, 용도 구분(typ) 클레임이 없으면
 * 수명이 긴 RefreshToken(1일)을 Authorization 헤더에 넣어 AccessToken처럼 쓸 수 있다.
 * 발급 시 용도를 명시하고, 각 필터가 자기 용도의 토큰만 받도록 강제한다.
 */
public final class TokenClaims {
    public static final String LOGIN_ID = "loginId";
    public static final String ROLE = "role";
    public static final String TYPE = "typ";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private TokenClaims() {
    }

    public static Map<String, Object> access(String loginId, String role) {
        return Map.of(LOGIN_ID, loginId, ROLE, role, TYPE, TYPE_ACCESS);
    }

    public static Map<String, Object> refresh(String loginId, String role) {
        return Map.of(LOGIN_ID, loginId, ROLE, role, TYPE, TYPE_REFRESH);
    }

    public static boolean isAccess(Map<String, Object> claims) {
        return TYPE_ACCESS.equals(claims.get(TYPE));
    }

    public static boolean isRefresh(Map<String, Object> claims) {
        return TYPE_REFRESH.equals(claims.get(TYPE));
    }
}
