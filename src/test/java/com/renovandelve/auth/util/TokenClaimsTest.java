package com.renovandelve.auth.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토큰 용도 구분 검증.
 * RefreshToken(수명 1일)이 AccessToken 자리에서 통과되지 않아야 한다.
 */
class TokenClaimsTest {

    private JwtUtil jwtUtil() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "key", "unit-test-secret-key-that-is-long-enough-0123456789");
        return jwtUtil;
    }

    @Test
    @DisplayName("발급된 토큰은 서명 검증 후에도 용도(typ)가 유지된다")
    void typeSurvivesRoundTrip() {
        JwtUtil jwtUtil = jwtUtil();

        Map<String, Object> access = jwtUtil.validateToken(
                jwtUtil.generateToken(TokenClaims.access("tester", "USER"), 30));
        Map<String, Object> refresh = jwtUtil.validateToken(
                jwtUtil.generateToken(TokenClaims.refresh("tester", "USER"), 60));

        assertThat(TokenClaims.isAccess(access)).isTrue();
        assertThat(TokenClaims.isRefresh(access)).isFalse();
        assertThat(TokenClaims.isRefresh(refresh)).isTrue();
        assertThat(TokenClaims.isAccess(refresh)).isFalse();
        assertThat(access.get(TokenClaims.LOGIN_ID)).isEqualTo("tester");
        assertThat(access.get(TokenClaims.ROLE)).isEqualTo("USER");
    }

    @Test
    @DisplayName("typ 클레임이 없는 이전 버전 토큰은 어느 용도로도 인정되지 않는다")
    void legacyTokenWithoutTypeIsRejected() {
        JwtUtil jwtUtil = jwtUtil();

        Map<String, Object> legacy = jwtUtil.validateToken(
                jwtUtil.generateToken(Map.of("loginId", "tester", "role", "ADMIN"), 30));

        assertThat(TokenClaims.isAccess(legacy)).isFalse();
        assertThat(TokenClaims.isRefresh(legacy)).isFalse();
    }
}
