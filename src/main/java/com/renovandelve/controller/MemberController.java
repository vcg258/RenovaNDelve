package com.renovandelve.controller;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.dto.MemberDTO;
import com.renovandelve.dto.request.SignupRequest;
import com.renovandelve.service.CurrentMemberService;
import com.renovandelve.service.MemberService;
import com.renovandelve.service.redis.SecurityTokenService;
import com.renovandelve.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "회원", description = "회원가입 / 내 정보 / 로그아웃")
@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MemberController {
    private final MemberService memberService;
    private final CurrentMemberService currentMemberService;
    private final SecurityTokenService securityTokenService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "회원 가입")
    @PostMapping("/auth/signup")
    public ResponseEntity<Void> signup(@RequestBody SignupRequest request) {
        memberService.signup(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/member/me")
    public ResponseEntity<MemberDTO> me() {
        MemberEntity member = currentMemberService.getCurrentMember();
        return ResponseEntity.ok(MemberDTO.builder()
                .memberId(member.getMemberId())
                .loginId(member.getLoginId())
                .createdAt(member.getCreatedAt())
                .build());
    }

    @Operation(summary = "로그아웃 (RefreshToken 폐기 + 쿠키 제거)")
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        // RefreshToken 쿠키에서 loginId를 해석해 Redis의 토큰을 폐기
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }
        if (refreshToken != null) {
            try {
                Map<String, Object> claims = jwtUtil.validateToken(refreshToken);
                String loginId = (String) claims.get("loginId");
                if (loginId != null) {
                    securityTokenService.logout(loginId);
                    log.info("로그아웃 처리 loginId={}", loginId);
                }
            } catch (Exception e) {
                log.warn("로그아웃 시 토큰 해석 실패: {}", e.getMessage());
            }
        }

        // 쿠키 즉시 만료
        ResponseCookie expired = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
                .build();
    }
}
