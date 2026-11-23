package com.renovandelve.auth.config;

import com.renovandelve.auth.handler.LoginAttemptService;
import com.renovandelve.auth.service.MemberDetailsService;
import com.renovandelve.auth.service.Oauth2UserService;
import com.renovandelve.auth.service.redis.SecurityTokenService;
import com.renovandelve.auth.service.redis.TokenRevocationService;
import com.renovandelve.auth.util.JwtUtil;
import com.renovandelve.auth.util.RefreshCookieFactory;
import com.renovandelve.auth.util.TokenClaims;
import com.renovandelve.global.config.CorsConfig;
import com.renovandelve.global.redis.RateLimitService;
import com.renovandelve.member.controller.AdminController;
import com.renovandelve.member.repository.MemberRepository;
import com.renovandelve.member.service.AdminService;
import com.renovandelve.member.service.CurrentMemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 규칙을 HTTP 요청으로 확인한다 (실제 SecurityConfig와 토큰 필터, 진짜 JWT).
 * 처음 고친 결함이 "로그인만 하면 관리자 API 호출 가능"이었으므로, 같은 구멍이 다시 생기면 여기서 걸린다.
 */
@WebMvcTest(controllers = AdminController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtUtil.class, RefreshCookieFactory.class})
@TestPropertySource(properties = {
        "renovaNDelve.jwt.secret=security-http-test-secret-key-0123456789abcdef",
        "renovaNDelve.cors.allowed-origins=http://localhost:5173",
})
class SecurityHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private AdminService adminService;
    @MockitoBean private CurrentMemberService currentMemberService;
    @MockitoBean private MemberDetailsService memberDetailsService;
    @MockitoBean private Oauth2UserService oauth2UserService;
    @MockitoBean private SecurityTokenService securityTokenService;
    @MockitoBean private MemberRepository memberRepository;
    @MockitoBean private LoginAttemptService loginAttemptService;
    @MockitoBean private RateLimitService rateLimitService;
    @MockitoBean private TokenRevocationService tokenRevocationService;

    private String bearer(String role) {
        return "Bearer " + jwtUtil.generateToken(TokenClaims.access("tester", role), 5);
    }

    @Test
    @DisplayName("관리자 API: 관리자 토큰이면 200")
    void adminCanCallAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/stats").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("관리자 API: 일반 회원 토큰이면 403")
    void userCannotCallAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/stats").header("Authorization", bearer("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 API: 토큰이 없으면 401")
    void anonymousCannotCallAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RefreshToken을 Bearer로 넣으면 관리자여도 거부한다")
    void refreshTokenIsNotAnAccessToken() throws Exception {
        String refresh = jwtUtil.generateToken(TokenClaims.refresh("tester", "ADMIN"), 5);
        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
    }

    // 위조·만료 토큰은 팀 원본 TokenError 규칙대로 403이다 (프론트는 401·403 모두 재발급을 시도한다)
    @Test
    @DisplayName("다른 키로 서명한 토큰은 거부한다 (403)")
    void foreignSignatureIsRejected() throws Exception {
        JwtUtil other = new JwtUtil();
        org.springframework.test.util.ReflectionTestUtils.setField(other, "key", "another-secret-key-0123456789abcdef-another");
        String forged = other.generateToken(TokenClaims.access("tester", "ADMIN"), 5);
        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + forged))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("폐기된 토큰(정지·로그아웃 이전 발급)은 만료 전이어도 만료로 거부한다 (403)")
    void revokedTokenIsRejected() throws Exception {
        when(tokenRevocationService.isRevoked(eq("tester"), anyLong())).thenReturn(true);
        mockMvc.perform(get("/api/admin/stats").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/api 밖(화면 파일)은 로그인 없이 열린다. 없는 파일이면 401이 아니라 404")
    void pagesOutsideApiArePublic() throws Exception {
        mockMvc.perform(get("/some-page"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("응답에 콘텐츠 보안 정책(CSP) 헤더가 붙는다")
    void contentSecurityPolicyHeader() throws Exception {
        mockMvc.perform(get("/api/admin/stats").header("Authorization", bearer("ADMIN")))
                .andExpect(header().string("Content-Security-Policy", SecurityConfig.CONTENT_SECURITY_POLICY));
    }
}
